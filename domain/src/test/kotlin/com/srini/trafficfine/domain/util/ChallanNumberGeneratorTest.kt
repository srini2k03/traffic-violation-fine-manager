package com.srini.trafficfine.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class ChallanNumberGeneratorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val issuedAt = LocalDate.of(2023, 9, 15).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `generates numbers in the documented pattern`() {
        val number = ChallanNumberGenerator(Random(42)).generate("tn", "TNP-4821", issuedAt, zone)
        assertTrue(number, ChallanNumberGenerator.PATTERN.matches(number))
        assertTrue(number.startsWith("EC-TN-20230915-4821-"))
    }

    @Test
    fun `pads short badge numbers and is deterministic for a seed`() {
        val a = ChallanNumberGenerator(Random(7)).generate("KA", "12", issuedAt, zone)
        val b = ChallanNumberGenerator(Random(7)).generate("KA", "12", issuedAt, zone)
        assertEquals(a, b)
        assertTrue(a.startsWith("EC-KA-20230915-0012-"))
    }

    @Test
    fun `different random state produces different suffixes`() {
        val gen = ChallanNumberGenerator(Random(1))
        assertNotEquals(gen.generate("TN", "1", issuedAt, zone), gen.generate("TN", "1", issuedAt, zone))
    }
}
