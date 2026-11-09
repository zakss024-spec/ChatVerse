package com.chatverse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class ChatVerseUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minimumSupportedVersionCode: Int,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: List<String>
) {
    val isMandatory: Boolean
        get() = BuildConfig.VERSION_CODE < minimumSupportedVersionCode

    val hasNewVersion: Boolean
        get() = versionCode > BuildConfig.VERSION_CODE

    val hasVerifiedReleaseUrl: Boolean
        get() = isOfficialReleaseUrl(downloadUrl)
}

private val updateHttpClient = OkHttpClient.Builder()
    .connectTimeout(7, TimeUnit.SECONDS)
    .readTimeout(7, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

private const val UPDATE_METADATA_URL =
    "https://raw.githubusercontent.com/zakss024-spec/ChatVerse/main/latest_version.json"

private fun isOfficialReleaseUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" &&
        uri.host.equals("github.com", ignoreCase = true) &&
        uri.path.orEmpty().startsWith("/zakss024-spec/ChatVerse/releases/download/")
}.getOrDefault(false)

internal suspend fun checkChatVerseUpdate(): ChatVerseUpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url(UPDATE_METADATA_URL)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        updateHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val versionCode = json.optInt("versionCode", -1)
            val versionName = json.optString("versionName").take(32)
            val minimum = json.optInt("minimumSupportedVersionCode", 1)
            val downloadUrl = json.optString("downloadUrl").take(2048)
            val sha256 = json.optString("sha256").take(64)
            val notesJson = json.optJSONArray("releaseNotes")
            val notes = buildList {
                if (notesJson != null) {
                    for (index in 0 until minOf(notesJson.length(), 8)) {
                        val note = notesJson.optString(index).trim().take(180)
                        if (note.isNotBlank()) add(note)
                    }
                }
            }
            if (versionCode < 1 || minimum < 1 || versionName.isBlank()) return@withContext null
            if (downloadUrl.isNotBlank() && !isOfficialReleaseUrl(downloadUrl)) return@withContext null
            ChatVerseUpdateInfo(versionCode, versionName, minimum, downloadUrl, sha256, notes)
                .takeIf { it.hasNewVersion || it.isMandatory }
        }
    }.getOrNull()
}

@Composable
internal fun ChatVerseUpdateDialog(
    update: ChatVerseUpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    val mandatory = update.isMandatory
    AlertDialog(
        onDismissRequest = { if (!mandatory) onLater() },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF101B1A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB7C8C2),
        title = {
            Text("ChatVerse Update Available", color = Color.White, fontSize = 21.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (mandatory) "Pembaruan ini diperlukan agar ChatVerse tetap aman dan kompatibel."
                    else "A new version of ChatVerse is available."
                )
                Text("Latest Version: ${update.versionName} (${update.versionCode})", color = Color(0xFF68F5C2))
                if (update.releaseNotes.isNotEmpty()) {
                    Text("What's New", color = Color.White)
                    update.releaseNotes.forEach { note ->
                        Text("• $note", fontSize = 13.sp)
                    }
                }
                if (!update.hasVerifiedReleaseUrl) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Rilis APK resmi belum dipublikasikan. Coba lagi nanti.",
                        color = Color(0xFFFFC38A),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateNow,
                enabled = update.hasVerifiedReleaseUrl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00C98B),
                    contentColor = Color(0xFF032018),
                    disabledContainerColor = Color(0xFF31443D),
                    disabledContentColor = Color(0xFF91A49B)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Update Now")
            }
        },
        dismissButton = {
            if (!mandatory) {
                TextButton(onClick = onLater) {
                    Text("Later", color = Color(0xFF68F5C2))
                }
            }
        }
    )
}
