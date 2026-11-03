package com.srini.trafficfine.domain.fine

import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.FineLine
import com.srini.trafficfine.domain.model.ViolationType
import javax.inject.Inject

/**
 * Escalation policy for repeat offenders. Multipliers are expressed in percent
 * so that all arithmetic stays in integer rupees (no floating-point money).
 *
 * `tiers[n]` is the multiplier applied when the vehicle has `n` prior offences of
 * the same type inside the [lookbackDays] window; counts beyond the last tier use
 * the last tier. [maxFinePerLine] caps a single line item.
 */
data class RepeatOffenderPolicy(
    val lookbackDays: Int = 365,
    val tiers: List<Int> = listOf(100, 150, 200),
    val maxFinePerLine: Long = 50_000,
) {
    init {
        require(lookbackDays > 0) { "lookbackDays must be positive" }
        require(tiers.isNotEmpty()) { "at least one tier is required" }
        require(tiers.all { it >= 100 }) { "multipliers below 100% would reduce a fine" }
        require(tiers.zipWithNext().all { (a, b) -> b >= a }) { "tiers must be non-decreasing" }
        require(maxFinePerLine > 0) { "maxFinePerLine must be positive" }
    }

    fun multiplierFor(priorOffences: Int): Int {
        require(priorOffences >= 0) { "priorOffences cannot be negative" }
        return tiers[priorOffences.coerceAtMost(tiers.lastIndex)]
    }

    val lookbackMillis: Long get() = lookbackDays * 24L * 60 * 60 * 1000

    companion object {
        val DEFAULT = RepeatOffenderPolicy()
    }
}

class FineCalculator @Inject constructor(
    val policy: RepeatOffenderPolicy,
) {

    /**
     * @param types offences being charged now (duplicates are ignored).
     * @param priorOffencesByCode number of earlier offences per violation code for the same vehicle.
     */
    fun calculate(
        types: Collection<ViolationType>,
        priorOffencesByCode: Map<String, Int> = emptyMap(),
    ): FineBreakdown {
        val lines = types
            .distinctBy { it.code }
            .sortedByDescending { it.baseFine }
            .map { type ->
                val prior = (priorOffencesByCode[type.code] ?: 0).coerceAtLeast(0)
                val multiplier = policy.multiplierFor(prior)
                val amount = applyMultiplier(type.baseFine, multiplier).coerceAtMost(policy.maxFinePerLine)
                FineLine(
                    code = type.code,
                    title = type.title,
                    section = type.section,
                    baseAmount = type.baseFine,
                    priorOffences = prior,
                    multiplierPercent = multiplier,
                    amount = amount,
                )
            }
        return FineBreakdown(lines)
    }

    companion object {
        /** Rounds half-up to the nearest rupee. */
        internal fun applyMultiplier(base: Long, percent: Int): Long {
            require(base >= 0) { "base fine cannot be negative" }
            return Math.addExact(Math.multiplyExact(base, percent.toLong()), 50) / 100
        }
    }
}
