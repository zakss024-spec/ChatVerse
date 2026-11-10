package com.chatverse.app

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object PushTokenManager {
    private const val PREFS = "chatverse_push_state"
    private const val TOKEN_KEY = "latest_fcm_token"
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun cacheToken(context: Context, token: String) {
        if (token.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN_KEY, token).apply()
    }

    fun cacheCurrentToken(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> cacheToken(context, token) }
    }

    suspend fun syncWithSupabase(context: Context, accessToken: String) = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || FirebaseApp.getApps(context).isEmpty()) return@withContext
        runCatching {
            val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_KEY, null)
                ?: Tasks.await(FirebaseMessaging.getInstance().token).also { cacheToken(context, it) }
            if (token.isBlank()) return@runCatching

            val userRequest = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/auth/v1/user")
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .get().build()
            val userId = client.newCall(userRequest).execute().use { response ->
                if (!response.isSuccessful) return@runCatching
                JSONObject(response.body?.string().orEmpty()).optString("id")
            }
            if (userId.isBlank()) return@runCatching

            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("user_id", userId)
                .put("token", token)
                .put("device_id", deviceId)
                .put("platform", "android")
                .put("updated_at", Instant.now().toString())
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/device_push_tokens?on_conflict=token")
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("ChatVerseFCM", "Token sync failed: HTTP ${response.code}")
                }
            }
        }.onFailure {
            android.util.Log.w("ChatVerseFCM", "FCM token sync unavailable", it)
        }
    }

    suspend fun unregisterFromSupabase(context: Context, accessToken: String) = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) return@withContext
        runCatching {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: return@runCatching
            val userRequest = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/auth/v1/user")
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .get().build()
            val userId = client.newCall(userRequest).execute().use { response ->
                if (!response.isSuccessful) return@runCatching
                JSONObject(response.body?.string().orEmpty()).optString("id")
            }
            if (userId.isBlank()) return@runCatching
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/device_push_tokens?user_id=eq.$userId&device_id=eq.$deviceId")
                .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete().build()
            client.newCall(request).execute().use { }
        }.onFailure {
            android.util.Log.w("ChatVerseFCM", "Could not unregister this device token", it)
        }
    }
}
