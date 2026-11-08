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
import java.net.URLEncoder
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
                    0 -> ChatsHubScreen(accessToken)
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

private data class SearchProfile(val id: String, val username: String, val displayName: String)
private data class ConversationPreview(val id: String, val type: String, val title: String, val peerId: String, val peerUsername: String, val peerName: String, val lastMessage: String)

@Composable
private fun ChatsHubScreen(accessToken: String) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<SearchProfile>()) }
    var status by remember { mutableStateOf("Cari username untuk memulai chat pribadi.") }
    var searching by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf("") }
    var activeTitle by remember { mutableStateOf("") }
    var showGlobal by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf(listOf<ConversationPreview>()) }
    var inboxStatus by remember { mutableStateOf("Memuat daftar chat...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accessToken) {
        while (true) {
            try {
                val response = supabaseCall("/rest/v1/rpc/list_my_conversations", accessToken, "POST", JSONObject())
                if (response.first in 200..299) {
                    val array = org.json.JSONArray(response.second)
                    val loaded = mutableListOf<ConversationPreview>()
                    for (i in 0 until array.length()) {
                        val row = array.getJSONObject(i)
                        loaded.add(ConversationPreview(
                            row.optString("conversation_id"),
                            row.optString("conversation_type"),
                            row.optString("conversation_title"),
                            row.optString("peer_id"),
                            row.optString("peer_username"),
                            row.optString("peer_display_name"),
                            row.optString("last_message")
                        ))
                    }
                    inbox = loaded
                    inboxStatus = if (loaded.isEmpty()) "Belum ada percakapan pribadi." else "Percakapan terbaru"
                } else inboxStatus = "Daftar chat belum dapat dimuat."
            } catch (_: Exception) {
                inboxStatus = "Periksa koneksi untuk memuat chat."
            }
            delay(10000)
        }
    }

    if (showGlobal) {
        Column {
            TextButton(onClick = { showGlobal = false }) { Text("← Kembali ke Chats", color = Mint) }
            GlobalChatScreen(accessToken)
        }
        return
    }
    if (activeId.isNotBlank()) {
        Column {
            TextButton(onClick = { activeId = ""; activeTitle = "" }) { Text("← Kembali ke Chats", color = Mint) }
            DirectChatScreen(accessToken, activeId, activeTitle)
        }
        return
    }

    Text("Chats", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Button(onClick = { showGlobal = true }, modifier = Modifier.fillMaxWidth()) { Text("Open ChatVerse Global") }
    Spacer(Modifier.height(14.dp))
    Text("Recent chats", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text(inboxStatus, color = Muted, fontSize = 11.sp)
    LazyColumn(modifier = Modifier.fillMaxWidth().height(170.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(inbox, key = { it.id }) { item ->
            val label = when {
                item.type == "global" -> "ChatVerse Global"
                item.type == "group" -> item.title.ifBlank { "Group chat" }
                else -> item.peerName.ifBlank { "@" + item.peerUsername }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    if (item.type == "global") showGlobal = true
                    else {
                        activeId = item.id
                        activeTitle = label
                    }
                }
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(item.lastMessage.ifBlank { "Mulai percakapan" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
    Text("New message", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(30) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Cari username...") }
    )
    Button(
        onClick = {
            searching = true
            status = "Mencari pengguna..."
            scope.launch {
                try {
                    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                    val response = supabaseCall("/rest/v1/profiles?username=ilike.*$encoded*&select=id,username,display_name&limit=20", accessToken)
                    if (response.first in 200..299) {
                        val array = org.json.JSONArray(response.second)
                        val found = mutableListOf<SearchProfile>()
                        for (i in 0 until array.length()) {
                            val row = array.getJSONObject(i)
                            found.add(SearchProfile(row.optString("id"), row.optString("username"), row.optString("display_name")))
                        }
                        results = found
                        status = if (found.isEmpty()) "Pengguna tidak ditemukan." else "Pilih pengguna untuk membuka chat."
                    } else status = "Pencarian gagal. Coba lagi."
                } catch (_: Exception) {
                    status = "Tidak dapat mencari pengguna. Periksa internet."
                } finally {
                    searching = false
                }
            }
        },
        enabled = query.length >= 2 && !searching,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (searching) "Searching..." else "Search users") }
    Spacer(Modifier.height(8.dp))
    Text(status, color = Muted, fontSize = 12.sp)
    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.id }) { profile ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        try {
                            val body = JSONObject().put("p_other_user", profile.id)
                            val response = supabaseCall("/rest/v1/rpc/open_direct_conversation", accessToken, "POST", body)
                            if (response.first in 200..299) {
                                activeId = response.second.trim().trim('"')
                                activeTitle = profile.displayName.ifBlank { "@" + profile.username }
                            } else status = "Tidak dapat membuka chat. Pastikan pengguna tidak memblokirmu."
                        } catch (_: Exception) {
                            status = "Gagal membuka chat. Periksa koneksi."
                        }
                    }
                }
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text(profile.displayName.ifBlank { profile.username }, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("@" + profile.username, color = Mint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DirectChatScreen(accessToken: String, conversationId: String, title: String) {
    var messages by remember { mutableStateOf(listOf<GlobalMessage>()) }
    var myUserId by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Memuat percakapan...") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accessToken, conversationId) {
        try {
            val user = supabaseCall("/auth/v1/user", accessToken)
            if (user.first !in 200..299) {
                status = "Sesi login perlu diperbarui."
                return@LaunchedEffect
            }
            myUserId = JSONObject(user.second).optString("id")
            while (true) {
                val response = supabaseCall("/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,content&order=created_at.asc&limit=100", accessToken)
                if (response.first in 200..299) {
                    val array = org.json.JSONArray(response.second)
                    val loaded = mutableListOf<GlobalMessage>()
                    for (i in 0 until array.length()) {
                        val row = array.getJSONObject(i)
                        loaded.add(GlobalMessage(row.optString("id"), row.optString("sender_id"), row.optString("content")))
                    }
                    messages = loaded
                    status = "Pesan diperbarui otomatis"
                } else status = "Gagal memuat pesan. Coba lagi."
                delay(4000)
            }
        } catch (_: Exception) {
            status = "Koneksi terputus. Periksa internet."
        }
    }

    Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
    Text(status, color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier = Modifier.fillMaxWidth().height(390.dp), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(messages, key = { it.id }) { item ->
            val mine = item.senderId == myUserId
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Box(Modifier.clip(RoundedCornerShape(17.dp)).background(if (mine) Color(0xFF24564F) else Panel).padding(horizontal = 13.dp, vertical = 10.dp)) {
                    Text(item.content, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (messages.isEmpty()) item { Text("Belum ada pesan. Sapa pengguna ini!", color = Muted, fontSize = 13.sp) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(2000) }, placeholder = { Text("Tulis pesan...") }, modifier = Modifier.weight(1f), maxLines = 4)
        Spacer(Modifier.width(8.dp))
        Button(enabled = draft.isNotBlank() && !sending && myUserId.isNotBlank(), onClick = {
            val content = draft.trim()
            sending = true
            scope.launch {
                try {
                    val body = JSONObject().put("conversation_id", conversationId).put("sender_id", myUserId).put("message_type", "text").put("content", content)
                    val response = supabaseCall("/rest/v1/messages", accessToken, "POST", body)
                    if (response.first in 200..299) draft = "" else status = "Pesan gagal dikirim."
                } catch (_: Exception) {
                    status = "Gagal mengirim pesan."
                } finally {
                    sending = false
                }
            }
        }) { Text(if (sending) "..." else "Send") }
    }
}

private data class GlobalMessage(val id: String, val senderId: String, val content: String)

@Composable
private fun GlobalChatScreen(accessToken: String) {
    var conversationId by remember { mutableStateOf("") }
    var myUserId by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<GlobalMessage>()) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Menghubungkan ke Global Chat...") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accessToken) {
        try {
            val user = supabaseCall("/auth/v1/user", accessToken)
            if (user.first !in 200..299) {
                status = "Sesi login perlu diperbarui."
                return@LaunchedEffect
            }
            myUserId = JSONObject(user.second).optString("id")
            val room = supabaseCall("/rest/v1/conversations?type=eq.global&title=eq.ChatVerse%20Global&select=id&limit=1", accessToken)
            if (room.first !in 200..299) {
                status = "Tidak dapat membuka ruang global (\${room.first})."
                return@LaunchedEffect
            }
            val rooms = org.json.JSONArray(room.second)
            if (rooms.length() == 0) {
                status = "Ruang Global belum tersedia untuk akun ini."
                return@LaunchedEffect
            }
            conversationId = rooms.getJSONObject(0).optString("id")
            status = "ChatVerse Global • pembaruan otomatis"
            while (true) {
                val result = supabaseCall("/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,content&order=created_at.asc&limit=100", accessToken)
                if (result.first in 200..299) {
                    val rows = org.json.JSONArray(result.second)
                    val loaded = mutableListOf<GlobalMessage>()
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        loaded.add(GlobalMessage(row.optString("id"), row.optString("sender_id"), row.optString("content")))
                    }
                    messages = loaded
                } else status = "Gagal memuat pesan (\${result.first})."
                delay(4000)
            }
        } catch (_: Exception) {
            status = "Koneksi terputus. Periksa internet."
        }
    }

    Text("Global Chat", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Text(status, color = Mint, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(430.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            val mine = message.senderId == myUserId
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Text(if (mine) "You" else "User " + message.senderId.take(6), color = if (mine) Mint else Cyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Box(Modifier.clip(RoundedCornerShape(17.dp)).background(if (mine) Color(0xFF24564F) else Panel).padding(horizontal = 13.dp, vertical = 10.dp)) {
                    Text(message.content, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (messages.isEmpty()) item { Text("Belum ada pesan. Mulai percakapan global pertama!", color = Muted, fontSize = 13.sp) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(2000) }, placeholder = { Text("Tulis pesan...") }, modifier = Modifier.weight(1f), maxLines = 4)
        Spacer(Modifier.width(8.dp))
        Button(
            enabled = draft.isNotBlank() && !sending && conversationId.isNotBlank() && myUserId.isNotBlank(),
            onClick = {
                val textToSend = draft.trim()
                sending = true
                scope.launch {
                    try {
                        val body = JSONObject().put("conversation_id", conversationId).put("sender_id", myUserId).put("message_type", "text").put("content", textToSend)
                        val result = supabaseCall("/rest/v1/messages", accessToken, "POST", body)
                        if (result.first in 200..299) {
                            draft = ""
                            status = "Pesan terkirim"
                        } else status = "Pesan gagal dikirim (\${result.first})."
                    } catch (_: Exception) {
                        status = "Gagal mengirim. Periksa koneksi."
                    } finally {
                        sending = false
                    }
                }
            }
        ) { Text(if (sending) "..." else "Send") }
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
                val profileResult = supabaseCall("/rest/v1/profiles?id=eq.$id&select=id,username,display_name,bio", accessToken)
                if (profileResult.first in 200..299) {
                    val rows = org.json.JSONArray(profileResult.second)
                    if (rows.length() > 0) {
                        val row = rows.getJSONObject(0)
                        username = row.optString("username")
                        displayName = row.optString("display_name")
                        bio = row.optString("bio")
                        status = "Profil tersinkron"
                    } else status = "Profil belum tersedia."
                } else status = "Gagal memuat profil (${profileResult.first})."
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
                val result = supabaseCall("/rest/v1/profiles?id=eq.$userId", accessToken, "PATCH", body)
                status = if (result.first in 200..299) "Profil berhasil disimpan." else "Gagal menyimpan (${result.first}). Username mungkin sudah dipakai."
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
