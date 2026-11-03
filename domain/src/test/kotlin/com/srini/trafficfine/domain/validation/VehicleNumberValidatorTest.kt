package com.srini.trafficfine.domain.validation

import com.srini.trafficfine.domain.validation.VehicleNumberValidator.Reason
import com.srini.trafficfine.domain.validation.VehicleNumberValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleNumberValidatorTest {

    private val validator = VehicleNumberValidator()

    @Test
    fun `accepts standard plates in any spacing and case`() {
        listOf("TN09AB1234", "tn 09 ab 1234", "TN-09-AB-1234", "MH12DE1433", "KA01MJ2023").forEach {
            assertTrue("$it should be valid", validator.isValid(it))
        }
    }

    @Test
    fun `normalizes and formats a standard plate`() {
        val result = validator.validate(" tn 9 ab-1234 ")
        assertEquals(Result.Valid("TN9AB1234", "TN 9 AB 1234", isBharatSeries = false), result)
    }

    @Test
    fun `accepts plates without a series and with a three letter series`() {
        assertTrue(validator.isValid("KA011234"))
        assertTrue(validator.isValid("DL3CAF0001"))
    }

    @Test
    fun `accepts Bharat series plates`() {
        val result = validator.validate("22 BH 1234 AB")
        assertEquals(Result.Valid("22BH1234AB", "22 BH 1234 AB", isBharatSeries = true), result)
        assertTrue(validator.isValid("21BH0001A"))
    }

    @Test
    fun `rejects empty input`() {
        assertEquals(Result.Invalid(Reason.EMPTY), validator.validate("  - "))
    }

    @Test
    fun `rejects too short and too long input`() {
        assertEquals(Result.Invalid(Reason.TOO_SHORT), validator.validate("TN1234"))
        assertEquals(Result.Invalid(Reason.TOO_LONG), validator.validate("TN09ABCD123456"))
    }

    @Test
    fun `rejects unknown state code`() {
        assertEquals(Result.Invalid(Reason.UNKNOWN_STATE_CODE), validator.validate("XY09AB1234"))
    }

    @Test
    fun `rejects zero RTO code`() {
        assertEquals(Result.Invalid(Reason.INVALID_RTO_CODE), validator.validate("TN00AB1234"))
    }

    @Test
    fun `rejects malformed plates`() {
        listOf("TN09AB12C4", "TNAB091234", "TN09ABCD1234", "TN09AB0000", "22BH12AB34").forEach {
            assertFalse("$it should be invalid", validator.isValid(it))
        }
    }
}
