package com.srini.trafficfine.domain.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Thin wrapper around [Clock] so time-dependent logic is deterministic in tests. */
class TimeProvider(private val clock: Clock) {
    fun now(): Long = clock.millis()

    fun zone(): ZoneId = clock.zone

    /** Start (inclusive) and end (exclusive) of the local day containing [millis]. */
    fun dayBounds(millis: Long = now()): LongRange {
        val date = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate()
        return dayBounds(date)
    }

    fun dayBounds(date: LocalDate): LongRange {
        val start = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return start until end
    }
}
