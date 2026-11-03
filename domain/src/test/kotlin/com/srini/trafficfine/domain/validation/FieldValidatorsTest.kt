package com.srini.trafficfine.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldValidatorsTest {

    @Test
    fun `normalizes Indian mobile numbers`() {
        assertEquals("9876543210", PhoneValidator.normalize("+91 98765 43210"))
        assertEquals("9876543210", PhoneValidator.normalize("919876543210"))
        assertEquals("9876543210", PhoneValidator.normalize("09876543210"))
        assertEquals("6123456789", PhoneValidator.normalize("612-345-6789"))
    }

    @Test
    fun `rejects invalid mobile numbers`() {
        assertNull(PhoneValidator.normalize("5876543210"))
        assertNull(PhoneValidator.normalize("98765"))
        assertFalse(PhoneValidator.isValid(""))
    }

    @Test
    fun `validates credentials`() {
        assertTrue(CredentialsValidator.isValidEmail("officer@tnpolice.gov.in"))
        assertFalse(CredentialsValidator.isValidEmail("officer@"))
        assertTrue(CredentialsValidator.isValidPassword("s3cretPass"))
        assertFalse(CredentialsValidator.isValidPassword("short"))
    }
}
