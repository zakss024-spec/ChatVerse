package com.chatverse.app

import android.os.Bundle
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private val httpClient = OkHttpClient()
private val jsonType = "application/json; charset=utf-8".toMediaType()

private suspend fun supabaseCall(path: String, token: String, method: String = "GET", payload: JSONObject? = null): Pair<Int, String> =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL + path)
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
        when (method) {
            "PATCH" -> builder.patch((payload ?: JSONObject()).toString().toRequestBody(jsonType))
            "POST" -> builder.post((payload ?: JSONObject()).toString().toRequestBody(jsonType))
            else -> builder.get()
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            response.code to response.body?.string().orEmpty()
        }
    }

@Composable
private fun AuthGate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("chatverse_session", android.content.Context.MODE_PRIVATE) }
    var accessToken by remember { mutableStateOf(prefs.getString("access_token", null)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    if (accessToken != null) {
        ChatVerseApp(accessToken = accessToken.orEmpty(), onLogout = {
            prefs.edit().clear().apply()
            accessToken = null
        })
        return
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10242A), Night, Color(0xFF080D12)))).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("ChatVerse", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Beyond Messaging", color = Mint, fontSize = 13.sp, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(12.dp))
            Text(if (isRegister) "Create your account" else "Welcome back", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (isRegister) OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message, color = Color(0xFFFFB4AB), fontSize = 12.sp)
            Button(onClick = {
                if (email.isBlank() || password.length < 6 || (isRegister && username.isBlank())) {
                    message = "Isi semua data. Password minimal 6 karakter."
                } else {
                    busy = true
                    message = "Menghubungkan ke ChatVerse..."
                    val payload = JSONObject().put("email", email.trim()).put("password", password)
                    if (isRegister) payload.put("data", JSONObject().put("username", username.trim()).put("display_name", username.trim()))
                    val endpoint = if (isRegister) "signup" else "token?grant_type=password"
                    val request = Request.Builder().url(BuildConfig.SUPABASE_URL + "/auth/v1/" + endpoint)
                        .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonType)).build()
                    httpClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            (context as? ComponentActivity)?.runOnUiThread {
                                busy = false
                                message = "Koneksi gagal: periksa internet lalu coba lagi."
                            }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body?.string().orEmpty()
                            val result = runCatching { JSONObject(body) }.getOrNull()
                            val token = result?.optString("access_token").orEmpty()
                            (context as? ComponentActivity)?.runOnUiThread {
                                busy = false
                                if (response.isSuccessful && token.isNotBlank()) {
                                    prefs.edit().putString("access_token", token).putString("email", email.trim()).apply()
                                    accessToken = token
                                    message = ""
                                } else if (response.isSuccessful && isRegister) {
                                    message = "Akun dibuat. Cek email untuk konfirmasi, lalu masuk."
                                    isRegister = false
                                } else {
                                    message = result?.optString("msg")?.takeIf { it.isNotBlank() }
                                        ?: result?.optString("message")?.takeIf { it.isNotBlank() }
                                        ?: result?.optString("error_description")?.takeIf { it.isNotBlank() }
                                        ?: "Gagal masuk. Periksa email dan password."
                                }
                            }
                        }
                    })
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Night)) {
                Text(if (busy) "Please wait..." else if (isRegister) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { isRegister = !isRegister; message = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (isRegister) "Already have an account? Sign in" else "New here? Create account", color = Cyan)
            }
            Text("Developer: Zakss", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private val Night = Color(0xFF071014)
private val Panel = Color(0xFF142329)
private val Mint = Color(0xFF68E5C1)
private val Cyan = Color(0xFF69CBE8)
private val Muted = Color(0xFF8FA5AA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthGate()
            }
        }
    }
}

@Composable
private fun ChatVerseApp(accessToken: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Night
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF10242A), Night, Color(0xFF080D12))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(22.dp))
                Header()
                Spacer(Modifier.height(28.dp))
                when (selectedTab) {
                    0 -> ChatsScreen()
                    1 -> GroupsScreen()
                    else -> ProfileScreen(accessToken, onLogout)
                }
                Spacer(Modifier.height(100.dp))
            }

            GlassNavigation(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "ChatVerse",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Beyond Messaging",
                color = Muted,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C3940))
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Text("Z", color = Mint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ChatsScreen() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Messages",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.Add, contentDescription = "New chat", tint = Mint)
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xAA1B3036))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted)
        Spacer(Modifier.width(10.dp))
        Text("Search conversations", color = Muted, fontSize = 14.sp)
    }
    Spacer(Modifier.height(22.dp))
    Text("RECENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(10.dp))

    val chats = listOf(
        ChatItem("ChatVerse Team", "Welcome to the new space ✨", "09:42", "C", true),
        ChatItem("Alya", "That sounds good!", "Yesterday", "A", false),
        ChatItem("Project Room", "You: I'll send the update soon", "Mon", "P", false),
        ChatItem("Raka", "See you tomorrow", "Sun", "R", false)
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(chats) { chat -> ChatRow(chat) }
    }
}

private data class ChatItem(
    val name: String,
    val message: String,
    val time: String,
    val initial: String,
    val unread: Boolean
)

@Composable
private fun ChatRow(chat: ChatItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xCC1B3036), Color(0xAA122329))))
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF28665E), Color(0xFF24506A)))),
            contentAlignment = Alignment.Center
        ) {
            Text(chat.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(chat.message, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Muted, fontSize = 10.sp)
            if (chat.unread) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Mint))
            }
        }
    }
}

@Composable
private fun GroupsScreen() {
    Text("Communities", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text("Your group conversations will appear here.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
    FeatureCard("Create a group", "Bring friends together in one conversation.")
    Spacer(Modifier.height(12.dp))
    FeatureCard("Discover conversations", "Find and join spaces shared with you.")
}

@Composable
private fun ProfileScreen(accessToken: String, onLogout: () -> Unit) {
    var userId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Memuat profil...") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(accessToken) {
        try {
            val userResult = supabaseCall("/auth/v1/user", accessToken)
            if (userResult.first !in 200..299) {
                status = "Sesi tidak valid. Silakan keluar lalu masuk kembali."
            } else {
                val id = JSONObject(userResult.second).optString("id")
                userId = id
                val profileResult = supabaseCall("/rest/v1/profiles?id=eq.\$id&select=id,username,display_name,bio", accessToken)
                if (profileResult.first in 200..299) {
                    val rows = org.json.JSONArray(profileResult.second)
                    if (rows.length() > 0) {
                        val row = rows.getJSONObject(0)
                        username = row.optString("username")
                        displayName = row.optString("display_name")
                        bio = row.optString("bio")
                        status = "Profil tersinkron"
                    } else status = "Profil belum tersedia."
                } else status = "Gagal memuat profil (\${profileResult.first})."
            }
        } catch (_: Exception) {
            status = "Tidak dapat terhubung. Periksa internet."
        }
    }

    Text("Your profile", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(status, color = Mint, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = username, onValueChange = { username = it.lowercase().replace(" ", "") }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text("Bio (maks. 160 karakter)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Button(onClick = { saving = true }, enabled = !saving && userId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(if (saving) "Menyimpan..." else "Simpan profil")
    }
    LaunchedEffect(saving) {
        if (saving && userId.isNotBlank()) {
            try {
                val body = JSONObject().put("display_name", displayName.trim()).put("username", username.trim()).put("bio", bio.trim())
                val result = supabaseCall("/rest/v1/profiles?id=eq.\$userId", accessToken, "PATCH", body)
                status = if (result.first in 200..299) "Profil berhasil disimpan." else "Gagal menyimpan (\${result.first}). Username mungkin sudah dipakai."
            } catch (_: Exception) {
                status = "Gagal menyimpan. Periksa koneksi."
            }
            saving = false
        }
    }
    Spacer(Modifier.height(20.dp))
    FeatureCard("Account security", "Akun terhubung ke Supabase Auth.")
    Spacer(Modifier.height(12.dp))
    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633C43))) {
        Text("Sign out", color = Color.White)
    }
}
@Composable
private fun FeatureCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GlassNavigation(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xE61A2C32))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationItem("Chats", Icons.Rounded.ChatBubbleOutline, 0, selected, onSelect)
        NavigationItem("Groups", Icons.Rounded.Groups, 1, selected, onSelect)
        NavigationItem("Profile", Icons.Rounded.PersonOutline, 2, selected, onSelect)
    }
}

@Composable
private fun NavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: Int,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val active = selected == index
    val tint by animateColorAsState(if (active) Mint else Muted, label = "navTint")
    val horizontalPadding by animateDpAsState(if (active) 17.dp else 12.dp, label = "navPadding")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0xFF294B4B) else Color.Transparent)
            .clickable { onSelect(index) }
            .padding(horizontal = horizontalPadding, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        if (active) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
