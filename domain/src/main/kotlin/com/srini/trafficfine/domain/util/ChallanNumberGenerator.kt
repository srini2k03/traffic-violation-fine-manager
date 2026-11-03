package com.srini.trafficfine.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Generates human-readable, collision-resistant e-challan numbers, e.g.
 * `EC-TN-20230915-4821-K7Q2XM`  (prefix - state - date - badge suffix - random).
 *
 * The random block uses an alphabet without ambiguous characters (0/O, 1/I).
 */
class ChallanNumberGenerator(
    private val random: Random = Random.Default,
) {
    fun generate(stateCode: String, badgeNumber: String, issuedAt: Long, zone: ZoneId): String {
        val date = DATE.format(Instant.ofEpochMilli(issuedAt).atZone(zone))
        val badge = badgeNumber.filter { it.isLetterOrDigit() }.takeLast(4).padStart(4, '0').uppercase()
        val state = stateCode.uppercase().take(2).ifEmpty { "XX" }
        val suffix = buildString { repeat(6) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        return "EC-$state-$date-$badge-$suffix"
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val PATTERN = Regex("^EC-[A-Z0-9]{2}-\\d{8}-[A-Z0-9]{4}-[$ALPHABET]{6}$")
    }
}
