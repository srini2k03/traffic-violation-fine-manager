package com.srini.trafficfine.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViolationFormValidatorTest {

    private val validator = ViolationFormValidator(VehicleNumberValidator())

    private val valid = ViolationDraft(
        vehicleNumber = "TN09AB1234",
        violationCodes = setOf("NO_HELMET"),
        latitude = 13.08,
        longitude = 80.27,
    )

    @Test
    fun `valid draft has no errors`() {
        assertTrue(validator.validate(valid).isEmpty())
    }

    @Test
    fun `collects every error at once`() {
        val draft = ViolationDraft(
            vehicleNumber = "bad",
            violationCodes = emptySet(),
            offenderPhone = "12345",
            notes = "x".repeat(ViolationFormValidator.MAX_NOTES_LENGTH + 1),
        )
        assertEquals(
            setOf(
                FormError.VEHICLE_NUMBER_INVALID,
                FormError.NO_VIOLATION_SELECTED,
                FormError.PHONE_INVALID,
                FormError.NOTES_TOO_LONG,
                FormError.LOCATION_MISSING,
            ),
            validator.validate(draft),
        )
    }

    @Test
    fun `location can be optional`() {
        val draft = valid.copy(latitude = null, longitude = null)
        assertTrue(validator.validate(draft, requireLocation = false).isEmpty())
    }

    @Test
    fun `photo evidence is mandatory for red light jumping`() {
        val draft = valid.copy(violationCodes = setOf("RED_LIGHT"))
        assertEquals(setOf(FormError.EVIDENCE_REQUIRED), validator.validate(draft))
        assertTrue(validator.validate(draft.copy(localEvidencePath = "/tmp/e.jpg")).isEmpty())
    }

    @Test
    fun `unknown violation code is rejected`() {
        assertEquals(setOf(FormError.UNKNOWN_VIOLATION), validator.validate(valid.copy(violationCodes = setOf("FOO"))))
    }
}
