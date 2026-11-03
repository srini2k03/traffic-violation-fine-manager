package com.srini.trafficfine.domain.util

import com.srini.trafficfine.domain.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChallanQrTest {

    @Test
    fun `payload contains challan, plate, amount and status only`() {
        val violation = TestData.violation("v1", issuedAt = 0, codes = listOf("NO_HELMET"))
            .copy(offenderName = "Ravi Kumar", offenderPhone = "9876543210")

        val payload = ChallanQr.payload(violation)

        assertEquals(
            "https://echallan.example.in/verify?c=EC-TN-20230915-4821-ABCDEF&v=TN09AB1234&a=1000&s=PENDING",
            payload,
        )
        assertFalse(payload.contains("9876543210"))
        assertFalse(payload.contains("Ravi"))
    }
}
