package com.imkolganov.datagate.freetier

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
}
