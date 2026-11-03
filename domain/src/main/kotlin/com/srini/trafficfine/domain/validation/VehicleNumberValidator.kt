package com.srini.trafficfine.domain.validation

import javax.inject.Inject

/**
 * Validates Indian vehicle registration numbers.
 *
 * Supported formats (spaces / hyphens are ignored, input is case-insensitive):
 *  - Standard state series: `SS RR X[XX] NNNN`, e.g. `TN 09 AB 1234`, `DL 3C AF 0001`, `KA 01 1234`
 *  - Bharat (BH) series:    `YY BH NNNN X[X]`, e.g. `22 BH 1234 AB`
 */
class VehicleNumberValidator @Inject constructor() {

    sealed interface Result {
        data class Valid(val normalized: String, val formatted: String, val isBharatSeries: Boolean) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason { EMPTY, TOO_SHORT, TOO_LONG, UNKNOWN_STATE_CODE, INVALID_RTO_CODE, INVALID_FORMAT }

    fun validate(raw: String): Result {
        val plate = normalize(raw)
        if (plate.isEmpty()) return Result.Invalid(Reason.EMPTY)
        if (plate.length < MIN_LENGTH) return Result.Invalid(Reason.TOO_SHORT)
        if (plate.length > MAX_LENGTH) return Result.Invalid(Reason.TOO_LONG)

        BH_SERIES.matchEntire(plate)?.let { m ->
            val (year, digits, letters) = m.destructured
            return Result.Valid(plate, "$year BH $digits $letters", isBharatSeries = true)
        }

        val match = STANDARD.matchEntire(plate) ?: return Result.Invalid(Reason.INVALID_FORMAT)
        val (state, rto, series, number) = match.destructured
        if (state !in STATE_CODES) return Result.Invalid(Reason.UNKNOWN_STATE_CODE)
        if (rto.trimStart('0').isEmpty()) return Result.Invalid(Reason.INVALID_RTO_CODE)
        if (number.all { it == '0' }) return Result.Invalid(Reason.INVALID_FORMAT)

        val formatted = listOf(state, rto, series, number).filter { it.isNotEmpty() }.joinToString(" ")
        return Result.Valid(plate, formatted, isBharatSeries = false)
    }

    fun isValid(raw: String): Boolean = validate(raw) is Result.Valid

    companion object {
        private const val MIN_LENGTH = 7
        private const val MAX_LENGTH = 11

        // State (2 letters) + RTO (1-2 digits) + series (0-3 letters) + number (4 digits).
        // Delhi's "DL 3C AF 0001" style therefore normalises to DL / 3 / CAF / 0001.
        private val STANDARD = Regex("^([A-Z]{2})(\\d{1,2})([A-Z]{0,3})(\\d{4})$")
        private val BH_SERIES = Regex("^(\\d{2})BH(\\d{4})([A-Z]{1,2})$")

        /** State / UT codes in use (including legacy OR, UA and new TG). */
        val STATE_CODES: Set<String> = setOf(
            "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR",
            "JH", "JK", "KA", "KL", "LA", "LD", "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR",
            "PB", "PY", "RJ", "SK", "TG", "TN", "TR", "TS", "UA", "UK", "UP", "WB",
        )

        fun normalize(raw: String): String =
            raw.uppercase().filterNot { it.isWhitespace() || it == '-' || it == '.' }
    }
}
