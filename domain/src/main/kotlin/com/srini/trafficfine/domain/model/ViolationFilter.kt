package com.srini.trafficfine.domain.model

/**
 * History search criteria. All fields are optional; `null` means "no constraint".
 * [fromMillis] is inclusive and [toMillis] exclusive.
 */
data class ViolationFilter(
    val plateQuery: String? = null,
    val status: ViolationStatus? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val officerId: String? = null,
) {
    fun matches(violation: Violation): Boolean {
        val query = plateQuery?.let(::normalizePlateQuery)?.takeIf { it.isNotEmpty() }
        if (query != null && !violation.vehicleNumber.contains(query) &&
            !violation.challanNumber.contains(query, ignoreCase = true)
        ) return false
        if (status != null && violation.status != status) return false
        if (fromMillis != null && violation.issuedAt < fromMillis) return false
        if (toMillis != null && violation.issuedAt >= toMillis) return false
        if (officerId != null && violation.officerId != officerId) return false
        return true
    }

    companion object {
        fun normalizePlateQuery(raw: String): String =
            raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.replace("-", "")
    }
}
