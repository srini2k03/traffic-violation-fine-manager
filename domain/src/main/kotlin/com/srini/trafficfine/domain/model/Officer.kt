package com.srini.trafficfine.domain.model

/** Role granted through a Firebase Auth custom claim (`role`). */
enum class Role {
    OFFICER,
    ADMIN;

    companion object {
        fun fromClaim(value: Any?): Role? = when (value?.toString()?.lowercase()) {
            "admin" -> ADMIN
            "officer" -> OFFICER
            else -> null
        }
    }
}

data class Officer(
    val uid: String,
    val name: String,
    val email: String,
    val badgeNumber: String,
    val station: String,
    val role: Role,
    val active: Boolean = true,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}
