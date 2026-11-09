package com.chatverse.app

/**
 * Pure input rules shared by the Android auth and profile forms.
 * The Edge Function repeats these checks on the server.
 */
internal object AuthValidation {
    private val usernamePattern = Regex("^[a-z0-9_.]{3,30}$")

    fun normalizeUsername(value: String): String =
        value.trim().lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }
            .take(30)

    fun isValidUsername(value: String): Boolean = usernamePattern.matches(value)

    fun isValidPassword(value: String): Boolean = value.length in 8..128

    fun passwordsMatch(password: String, confirmation: String): Boolean =
        password == confirmation
}
