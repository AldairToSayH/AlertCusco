package com.example.evaluacin_consolidado2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionConfigTest {
    @Test
    fun acceptsPeruvianMobileWithoutAskingForCountryCode() {
        assertTrue(ProtectionConfig.validNumber("917328247"))
        assertEquals("+51917328247", ProtectionConfig.dialNumber("917328247"))
    }

    @Test
    fun supportsPreviouslySavedCountryCode() {
        assertTrue(ProtectionConfig.validNumber("+51917328247"))
        assertEquals("917328247", ProtectionConfig.localNumber("+51917328247"))
        assertEquals("+51917328247", ProtectionConfig.dialNumber("+51917328247"))
    }

    @Test
    fun rejectsOtherNumbers() {
        assertFalse(ProtectionConfig.validNumber("91732824"))
        assertFalse(ProtectionConfig.validNumber("+54917328247"))
        assertFalse(ProtectionConfig.validNumber("017328247"))
    }
}
