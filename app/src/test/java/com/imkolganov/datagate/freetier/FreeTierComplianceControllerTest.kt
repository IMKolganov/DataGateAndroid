package com.imkolganov.datagate.freetier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTierComplianceControllerTest {

    @Test
    fun setOnboardingVisible_updatesFlow() {
        FreeTierComplianceController.setOnboardingVisible(true)
        assertTrue(FreeTierComplianceController.onboardingVisible.value)

        FreeTierComplianceController.setOnboardingVisible(false)
        assertFalse(FreeTierComplianceController.onboardingVisible.value)
    }

    @Test
    fun requestStatusRefresh_incrementsNonce() {
        val before = FreeTierComplianceController.statusRefreshNonce.value
        FreeTierComplianceController.requestStatusRefresh()
        assertEquals(before + 1, FreeTierComplianceController.statusRefreshNonce.value)
    }
}
