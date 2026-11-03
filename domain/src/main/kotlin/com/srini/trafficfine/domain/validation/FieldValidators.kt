package com.srini.trafficfine.domain.validation

object PhoneValidator {
    private val INDIAN_MOBILE = Regex("^[6-9]\\d{9}$")

    /** Returns the 10-digit mobile number, or null if the input is not a valid Indian mobile. */
    fun normalize(raw: String): String? {
        var digits = raw.filter { it.isDigit() || it == '+' }
        digits = when {
            digits.startsWith("+91") -> digits.removePrefix("+91")
            digits.length == 12 && digits.startsWith("91") -> digits.removePrefix("91")
            digits.length == 11 && digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
        return digits.takeIf { INDIAN_MOBILE.matches(it) }
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null
}

object CredentialsValidator {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 8

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
