package com.srini.trafficfine.domain.model

data class DashboardStats(
    val issuedToday: Int,
    val paidToday: Int,
    val amountIssuedToday: Long,
    val amountCollectedToday: Long,
    val pendingCount: Int,
    val pendingAmount: Long,
    val unsyncedCount: Int,
    val topViolations: List<Pair<String, Int>>,
) {
    companion object {
        val EMPTY = DashboardStats(0, 0, 0, 0, 0, 0, 0, emptyList())

        /** Pure aggregation so it can be unit tested and reused by any data source. */
        fun from(violations: List<Violation>, dayStart: Long, dayEnd: Long): DashboardStats {
            val today = violations.filter { it.issuedAt in dayStart until dayEnd }
            val paidToday = violations.filter { v ->
                v.status == ViolationStatus.PAID && v.payment?.paidAt?.let { it in dayStart until dayEnd } == true
            }
            val pending = violations.filter { it.status == ViolationStatus.PENDING }
            val top = today.flatMap { it.typeCodes }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(3)
                .map { it.key to it.value }
            return DashboardStats(
                issuedToday = today.size,
                paidToday = paidToday.size,
                amountIssuedToday = today.sumOf { it.totalFine },
                amountCollectedToday = paidToday.sumOf { it.totalFine },
                pendingCount = pending.size,
                pendingAmount = pending.sumOf { it.totalFine },
                unsyncedCount = violations.count { it.syncState == SyncState.PENDING },
                topViolations = top,
            )
        }
    }
}
