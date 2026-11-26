package com.chatverse.app

/**
 * Pure input rules shared by the Android auth and profile forms.
 * Usernames are case-insensitive on the backend, but the input keeps the
 * exact letter case typed by the user. The Edge Function canonicalizes to
 * lowercase before lookup or registration.
 */
internal object AuthValidation {
    private val usernamePattern = Regex("^[A-Za-z0-9_.]{3,30}$")

    fun normalizeUsername(value: String): String =
        value.trim()
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '.' }
            .take(30)

    fun isValidUsername(value: String): Boolean = usernamePattern.matches(value)

    fun isValidPassword(value: String): Boolean = value.length in 8..128

    fun passwordsMatch(password: String, confirmation: String): Boolean =
        password == confirmation
}
