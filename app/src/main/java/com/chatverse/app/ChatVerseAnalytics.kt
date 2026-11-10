package com.chatverse.app

import com.posthog.PostHog

/**
 * Anonymous, privacy-first product analytics.
 * Never pass message content, credentials, tokens, usernames, emails, or profile data.
 */
object ChatVerseAnalytics {
    @Volatile
    private var enabled = false

    internal fun enable() {
        enabled = true
    }

    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        PostHog.capture(event = event, properties = properties)
    }

    fun reset() {
        if (enabled) PostHog.reset()
    }
}
