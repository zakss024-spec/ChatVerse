package com.chatverse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Refreshes Supabase sessions once after an authenticated request receives HTTP 401. */
internal object SessionRefresh {
    @Volatile
    var accessToken: String = ""
        private set

    @Volatile
    private var refreshToken: String = ""

    @Volatile
    var onRenewed: ((access: String, refresh: String) -> Unit)? = null

    @Volatile
    var onInvalid: (() -> Unit)? = null

    private val mutex = Mutex()
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun set(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
    }

    fun clear() {
        accessToken = ""
        refreshToken = ""
    }

    suspend fun refresh(failedToken: String): String? = mutex.withLock {
        // Another request may have refreshed the session while this caller waited.
        val current = accessToken
        if (current.isNotBlank() && current != failedToken) return@withLock current

        val currentRefresh = refreshToken
        if (currentRefresh.isBlank()) return@withLock null

        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("refresh_token", currentRefresh)
                    .toString()
                    .toRequestBody(jsonMedia)
                val request = Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    Triple(
                        response.code,
                        response.isSuccessful,
                        runCatching { JSONObject(responseText) }.getOrNull()
                    )
                }
            }.getOrNull()
        } ?: return@withLock null

        val (status, successful, json) = outcome
        val renewedAccess = json?.optString("access_token").orEmpty()
        if (successful && renewedAccess.isNotBlank()) {
            val renewedRefresh = json?.optString("refresh_token").orEmpty().ifBlank { currentRefresh }
            set(renewedAccess, renewedRefresh)
            withContext(Dispatchers.Main) {
                onRenewed?.invoke(renewedAccess, renewedRefresh)
            }
            return@withLock renewedAccess
        }

        // Only auth rejection means the session is invalid. Rate limits and server
        // errors must not sign the user out.
        if (status == 400 || status == 401 || status == 403) {
            clear()
            withContext(Dispatchers.Main) { onInvalid?.invoke() }
        }
        null
    }
}
