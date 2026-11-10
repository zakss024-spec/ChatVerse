package com.chatverse.app

import android.app.Application
import com.posthog.PersonProfiles
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class ChatVerseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val apiKey = BuildConfig.POSTHOG_API_KEY
        val host = BuildConfig.POSTHOG_HOST
        if (apiKey.isNotBlank() && host.isNotBlank()) {
            val config = PostHogAndroidConfig(apiKey = apiKey, host = host).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = false
                captureDeepLinks = false
                setDefaultPersonProperties = false
                personProfiles = PersonProfiles.NEVER
                sessionReplay = false
                errorTrackingConfig.autoCapture = false
            }
            PostHogAndroid.setup(this, config)
            ChatVerseAnalytics.enable()
        }

        // FCM initializes automatically when google-services.json is present.
        // The app remains usable while Firebase is not configured yet.
        runCatching { PushTokenManager.cacheCurrentToken(this) }
    }
}
