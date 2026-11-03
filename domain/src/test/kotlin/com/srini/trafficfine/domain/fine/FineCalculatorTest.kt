package com.srini.trafficfine.domain.fine

import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FineCalculatorTest {

    private val calculator = FineCalculator(RepeatOffenderPolicy.DEFAULT)
    private val helmet = ViolationCatalogue.require("NO_HELMET") // 1,000
    private val drunk = ViolationCatalogue.require("DRUNK_DRIVING") // 10,000
    private val overload = ViolationCatalogue.require("OVERLOADING") // 20,000

    @Test
    fun `first offence charges the base fine`() {
        val result = calculator.calculate(listOf(helmet))
        assertEquals(1_000, result.total)
        assertEquals(100, result.lines.single().multiplierPercent)
        assertFalse(result.isRepeatOffence)
    }

    @Test
    fun `multiple offences are summed and ordered by base fine`() {
        val result = calculator.calculate(listOf(helmet, drunk))
        assertEquals(11_000, result.total)
        assertEquals(listOf("DRUNK_DRIVING", "NO_HELMET"), result.lines.map { it.code })
    }

    @Test
    fun `duplicate offences are charged once`() {
        assertEquals(1_000, calculator.calculate(listOf(helmet, helmet)).total)
    }

    @Test
    fun `repeat offender multiplier escalates per prior count`() {
        assertEquals(1_500, calculator.calculate(listOf(helmet), mapOf("NO_HELMET" to 1)).total)
        assertEquals(2_000, calculator.calculate(listOf(helmet), mapOf("NO_HELMET" to 2)).total)
        assertEquals(2_000, calculator.calculate(listOf(helmet), mapOf("NO_HELMET" to 9)).total)
    }

    @Test
    fun `multiplier only applies to the repeated offence type`() {
        val result = calculator.calculate(listOf(helmet, drunk), mapOf("NO_HELMET" to 1))
        assertEquals(1_500 + 10_000, result.total)
        assertTrue(result.isRepeatOffence)
    }

    @Test
    fun `line amount is capped by policy`() {
        val result = calculator.calculate(listOf(overload), mapOf("OVERLOADING" to 5))
        assertEquals(40_000, result.total)
        val capped = FineCalculator(RepeatOffenderPolicy(maxFinePerLine = 25_000))
            .calculate(listOf(overload), mapOf("OVERLOADING" to 5))
        assertEquals(25_000, capped.total)
    }

    @Test
    fun `negative prior counts are treated as zero`() {
        assertEquals(1_000, calculator.calculate(listOf(helmet), mapOf("NO_HELMET" to -3)).total)
    }

    @Test
    fun `rounds half up to whole rupees`() {
        assertEquals(750, FineCalculator.applyMultiplier(500, 150))
        assertEquals(500, FineCalculator.applyMultiplier(333, 150)) // 499.5 -> 500
        assertEquals(752, FineCalculator.applyMultiplier(501, 150)) // 751.5 -> 752
        assertEquals(498, FineCalculator.applyMultiplier(332, 150))
    }

    @Test
    fun `empty input yields zero`() {
        assertEquals(0, calculator.calculate(emptyList<ViolationType>()).total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `policy rejects decreasing tiers`() {
        RepeatOffenderPolicy(tiers = listOf(100, 200, 150))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `policy rejects discounts`() {
        RepeatOffenderPolicy(tiers = listOf(90))
    }
}
