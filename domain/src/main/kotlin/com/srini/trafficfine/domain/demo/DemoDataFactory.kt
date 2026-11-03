package com.srini.trafficfine.domain.demo

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.PriorOffenceCounter
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.util.ChallanNumberGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

/**
 * Builds a realistic, deterministic month of station activity for demo sessions: challans
 * spread over the last [DAYS] days (relative to the current clock), repeat offenders priced
 * with the real [FineCalculator], a mix of pending / paid / cancelled challans and the
 * matching officer activity log.
 *
 * The same clock always produces the same data, which keeps screenshots and tests stable.
 */
class DemoDataFactory @Inject constructor(
    private val calculator: FineCalculator,
    private val plateValidator: VehicleNumberValidator,
    private val time: TimeProvider,
) {

    fun create(): DemoSeed {
        val random = Random(SEED)
        val challanNumbers = ChallanNumberGenerator(Random(SEED + 1))
        val now = time.now()
        val today = Instant.ofEpochMilli(now).atZone(time.zone()).toLocalDate()

        val violations = mutableListOf<Violation>()
        for (daysAgo in DAYS - 1 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            issueTimes(date, daysAgo, now, random).forEach { issuedAt ->
                violations += newViolation(
                    index = violations.size,
                    issuedAt = issuedAt,
                    daysAgo = daysAgo,
                    now = now,
                    history = violations,
                    random = random,
                    challanNumbers = challanNumbers,
                )
            }
        }
        val ordered = violations.sortedByDescending { it.issuedAt }
        return DemoSeed(ordered, activityLog(ordered))
    }

    private fun issueTimes(date: LocalDate, daysAgo: Int, now: Long, random: Random): List<Long> {
        val start = time.dayBounds(date).first
        if (daysAgo == 0) {
            // Today: a handful of challans in the hours before "now" so the dashboard has numbers.
            return (1..TODAY_COUNT).map { i -> now - i * (25 + random.nextInt(35)) * MINUTE }
                .filter { it >= start }
                .sorted()
        }
        val count = 1 + random.nextInt(4)
        return (0 until count)
            .map { start + SHIFT_START + random.nextLong(SHIFT_LENGTH) }
            .sorted()
    }

    @Suppress("LongParameterList")
    private fun newViolation(
        index: Int,
        issuedAt: Long,
        daysAgo: Int,
        now: Long,
        history: List<Violation>,
        random: Random,
        challanNumbers: ChallanNumberGenerator,
    ): Violation {
        val officer = pickOfficer(random, daysAgo)
        val rawPlate = if (random.nextInt(100) < HABITUAL_SHARE) HABITUAL.random(random) else PLATES.random(random)
        val plate = plateValidator.validate(rawPlate) as VehicleNumberValidator.Result.Valid
        val codes = pickOffences(random, plate.normalized)
        val sameVehicle = history.filter { it.vehicleNumber == plate.normalized && it.issuedAt < issuedAt }
        val priors = PriorOffenceCounter.count(sameVehicle, issuedAt - calculator.policy.lookbackMillis)
            .filterKeys { it in codes }
        val fine = calculator.calculate(codes.map(ViolationCatalogue::require), priors)
        val place = PLACES.random(random)
        val offender = if (random.nextInt(100) < 80) OFFENDERS.random(random) else null
        val stateCode = if (plate.isBharatSeries) "BH" else plate.normalized.take(2)

        val roll = random.nextInt(100)
        val status = when {
            daysAgo == 0 -> if (roll < 35) ViolationStatus.PAID else ViolationStatus.PENDING
            roll < 60 -> ViolationStatus.PAID
            roll < 92 -> ViolationStatus.PENDING
            else -> ViolationStatus.CANCELLED
        }
        val payment = if (status == ViolationStatus.PAID) {
            val mode = PaymentMode.entries.random(random)
            val paidAt = minOf(now - MINUTE, issuedAt + (5 + random.nextInt(60 * 48)) * MINUTE)
                .coerceAtLeast(issuedAt + MINUTE)
            Payment(
                mode = mode,
                reference = if (mode == PaymentMode.CASH) "CASH-${100_000 + random.nextInt(900_000)}"
                else "${mode.name}-${10_000_000 + random.nextInt(90_000_000)}",
                paidAt = paidAt,
                collectedBy = officer.uid,
            )
        } else {
            null
        }

        return Violation(
            id = "${DemoData.UID_PREFIX}v-%03d".format(index + 1),
            challanNumber = challanNumbers.generate(stateCode, officer.badgeNumber, issuedAt, time.zone()),
            vehicleNumber = plate.normalized,
            offenderName = offender?.first,
            offenderPhone = offender?.second,
            fine = fine,
            location = GeoPoint(
                latitude = place.latitude + (random.nextDouble() - 0.5) / 500,
                longitude = place.longitude + (random.nextDouble() - 0.5) / 500,
                accuracyMeters = 4f + random.nextInt(12),
                address = place.address,
            ),
            evidencePhotoUrl = null,
            localEvidencePath = null,
            notes = if (random.nextInt(100) < 30) NOTES.random(random) else "",
            officerId = officer.uid,
            officerName = officer.name,
            officerBadge = officer.badgeNumber,
            issuedAt = issuedAt,
            status = status,
            payment = payment,
            cancelReason = if (status == ViolationStatus.CANCELLED) CANCEL_REASONS.random(random) else null,
            syncState = SyncState.SYNCED,
            updatedAt = payment?.paidAt ?: issuedAt,
        )
    }

    private fun pickOfficer(random: Random, daysAgo: Int): Officer {
        val roll = random.nextInt(100)
        return when {
            roll < 45 -> DemoData.OFFICER
            roll < 80 -> DemoData.OFFICER_2
            // The deactivated officer only appears in older records.
            roll < 95 && daysAgo > 10 -> DemoData.OFFICER_3
            roll < 95 -> DemoData.OFFICER_2
            else -> DemoData.ADMIN
        }
    }

    private fun pickOffences(random: Random, plate: String): List<String> {
        val first = weightedCode(random)
        val codes = linkedSetOf(first)
        if (random.nextInt(100) < 30) codes += weightedCode(random)
        // Habitual offenders keep riding without a helmet, which shows the repeat-offender multiplier.
        if (plate in HABITUAL_NORMALISED && random.nextBoolean()) codes += "NO_HELMET"
        return codes.toList()
    }

    private fun weightedCode(random: Random): String {
        var pick = random.nextInt(OFFENCE_WEIGHTS.sumOf { it.second })
        for ((code, weight) in OFFENCE_WEIGHTS) {
            if (pick < weight) return code
            pick -= weight
        }
        return OFFENCE_WEIGHTS.first().first
    }

    private fun activityLog(violations: List<Violation>): List<OfficerLog> {
        val logs = mutableListOf<OfficerLog>()
        fun log(officerId: String, action: LogAction, details: String, ref: String?, at: Long) {
            logs += OfficerLog(
                id = "${DemoData.UID_PREFIX}l-%04d".format(logs.size + 1),
                officerId = officerId,
                action = action,
                details = details,
                referenceId = ref,
                timestamp = at,
                syncState = SyncState.SYNCED,
            )
        }

        violations.groupBy { it.officerId to dayOf(it.issuedAt) }.forEach { (key, dayRows) ->
            val first = dayRows.minOf { it.issuedAt }
            val role = DemoData.officerByUid(key.first)?.role?.name?.lowercase() ?: "officer"
            log(key.first, LogAction.SIGN_IN, "Signed in as $role", null, first - 12 * MINUTE)
        }
        for (v in violations) {
            val formatted = (plateValidator.validate(v.vehicleNumber) as? VehicleNumberValidator.Result.Valid)
                ?.formatted ?: v.vehicleNumber
            log(v.officerId, LogAction.VIOLATION_ISSUED, "${v.challanNumber} · $formatted · ₹${v.totalFine}", v.id, v.issuedAt)
            v.payment?.let {
                log(it.collectedBy, LogAction.FINE_PAID, "${v.challanNumber} · ₹${v.totalFine} via ${it.mode.name}", v.id, it.paidAt)
            }
            v.cancelReason?.let {
                log(DemoData.ADMIN.uid, LogAction.CHALLAN_CANCELLED, "${v.challanNumber} · $it", v.id, v.updatedAt)
            }
        }
        return logs.sortedByDescending { it.timestamp }
    }

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(time.zone()).toLocalDate()

    private data class Place(val latitude: Double, val longitude: Double, val address: String)

    companion object {
        const val DAYS = 30
        const val TODAY_COUNT = 6
        private const val SEED = 20_230_915L
        private const val MINUTE = 60_000L
        private const val SHIFT_START = 8 * 60 * MINUTE
        private const val SHIFT_LENGTH = 12 * 60 * MINUTE
        private const val HABITUAL_SHARE = 25

        private val HABITUAL = listOf("TN 09 AB 1234", "TN 07 CQ 4521", "22 BH 1234 AB")
        private val HABITUAL_NORMALISED = HABITUAL.map(VehicleNumberValidator::normalize).toSet()

        /** Valid registrations across states, including Delhi-style and Bharat (BH) series. */
        val PLATES = listOf(
            "TN 09 AB 1234", "TN 07 CQ 4521", "TN 22 DK 7788", "TN 01 AX 0420", "TN 10 BM 3391",
            "TN 05 EF 6612", "TN 14 G 9087", "KA 01 MJ 2203", "KA 05 NP 9910", "AP 16 CD 5521",
            "KL 07 BZ 1188", "MH 12 QW 4410", "DL 3C AF 0001", "22 BH 1234 AB", "23 BH 4521 C",
            "TS 09 EP 7002", "PY 01 AC 3344", "TN 66 Z 2024",
        )

        private val OFFENCE_WEIGHTS = listOf(
            "NO_HELMET" to 22, "NO_SEATBELT" to 14, "OVER_SPEEDING" to 12, "WRONG_PARKING" to 12,
            "RED_LIGHT" to 9, "MOBILE_PHONE" to 8, "TRIPLE_RIDING" to 6, "NO_LICENCE" to 5,
            "NO_INSURANCE" to 5, "NO_PUC" to 3, "DRUNK_DRIVING" to 2, "NO_REGISTRATION" to 1, "OVERLOADING" to 1,
        )

        private val PLACES = listOf(
            Place(13.0067, 80.2570, "LB Road, Adyar, Chennai 600020"),
            Place(13.0012, 80.2565, "Adyar Signal, Sardar Patel Road, Chennai 600020"),
            Place(13.0108, 80.2420, "Kotturpuram High Road, Chennai 600085"),
            Place(12.9985, 80.2714, "Besant Nagar 2nd Avenue, Chennai 600090"),
            Place(13.0170, 80.2687, "Greenways Road Junction, Chennai 600028"),
            Place(12.9908, 80.2489, "Gandhi Nagar 1st Main Road, Chennai 600020"),
            Place(13.0039, 80.2390, "Gandhi Mandapam Road, Chennai 600025"),
        )

        private val OFFENDERS = listOf(
            "Ravi Kumar" to "9876543210", "Lakshmi Narayanan" to "9840012345", "Arun Prakash" to null,
            "Fathima Begum" to "9003456789", "Suresh Babu" to "9790011223", "Deepa S" to null,
            "Mohammed Irfan" to "8939012345", "Karthik R" to "7299123456", "Anitha Joseph" to "9445566778",
            "Vignesh Iyer" to null,
        )

        private val NOTES = listOf(
            "Rider argued, licence verified on the spot.",
            "Vehicle parked across the bus stop.",
            "Pillion also without helmet; counselled.",
            "Caught on speed gun at 72 km/h in a 40 zone.",
            "Offender requested UPI payment link.",
        )

        private val CANCEL_REASONS = listOf(
            "Duplicate entry for the same stop",
            "Wrong registration number recorded",
            "Emergency vehicle on duty",
        )
    }
}
