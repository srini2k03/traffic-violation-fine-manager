package com.srini.trafficfine.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyPoint(val date: LocalDate, val count: Int, val amount: Long, val collected: Long)

data class OfficerPerformance(
    val officerId: String,
    val officerName: String,
    val issued: Int,
    val amount: Long,
    val collected: Long,
)

/** Station-wide analytics for the admin console, derived purely from violation records. */
data class AnalyticsSummary(
    val days: List<DailyPoint>,
    val totalIssued: Int,
    val totalAmount: Long,
    val totalCollected: Long,
    val byStatus: Map<ViolationStatus, Int>,
    val byCategory: List<Pair<ViolationType.Category, Int>>,
    val topOffences: List<Pair<String, Int>>,
    val officers: List<OfficerPerformance>,
    val repeatOffenderShare: Double,
) {
    /** Share of issued (non-cancelled) amount that has been collected, 0.0–1.0. */
    val collectionRate: Double
        get() = if (totalAmount == 0L) 0.0 else totalCollected.toDouble() / totalAmount

    companion object {
        val EMPTY = AnalyticsSummary(emptyList(), 0, 0, 0, emptyMap(), emptyList(), emptyList(), emptyList(), 0.0)

        /**
         * @param today last day of the window (inclusive); the window covers [dayCount] local days.
         */
        fun from(violations: List<Violation>, today: LocalDate, dayCount: Int, zone: ZoneId): AnalyticsSummary {
            require(dayCount > 0) { "dayCount must be positive" }
            val firstDay = today.minusDays(dayCount - 1L)
            fun Violation.day(): LocalDate = Instant.ofEpochMilli(issuedAt).atZone(zone).toLocalDate()

            val inWindow = violations.filter { v -> v.day().let { !it.isBefore(firstDay) && !it.isAfter(today) } }
            val billable = inWindow.filter { it.status != ViolationStatus.CANCELLED }
            val collectedOf: (Violation) -> Long = { if (it.status == ViolationStatus.PAID) it.totalFine else 0L }

            val perDay = billable.groupBy { it.day() }
            val days = (0 until dayCount).map { offset ->
                val date = firstDay.plusDays(offset.toLong())
                val list = perDay[date].orEmpty()
                DailyPoint(date, list.size, list.sumOf { it.totalFine }, list.sumOf(collectedOf))
            }

            val codes = billable.flatMap { it.typeCodes }
            val byCategory = codes
                .mapNotNull { ViolationCatalogue.find(it)?.category }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<ViolationType.Category, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { it.key to it.value }
            val topOffences = codes.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key to it.value }

            val officers = billable.groupBy { it.officerId }.map { (id, list) ->
                OfficerPerformance(
                    officerId = id,
                    officerName = list.first().officerName,
                    issued = list.size,
                    amount = list.sumOf { it.totalFine },
                    collected = list.sumOf(collectedOf),
                )
            }.sortedWith(compareByDescending<OfficerPerformance> { it.issued }.thenBy { it.officerName })

            return AnalyticsSummary(
                days = days,
                totalIssued = billable.size,
                totalAmount = billable.sumOf { it.totalFine },
                totalCollected = billable.sumOf(collectedOf),
                byStatus = ViolationStatus.entries.associateWith { s -> inWindow.count { it.status == s } },
                byCategory = byCategory,
                topOffences = topOffences,
                officers = officers,
                repeatOffenderShare = if (billable.isEmpty()) 0.0 else billable.count { it.fine.isRepeatOffence }.toDouble() / billable.size,
            )
        }
    }
}
