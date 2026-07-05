package com.imkolganov.datagate.i18n

import org.junit.Assert.assertTrue
import org.junit.Test

class AllLocalesHaveStringKeysTest {

    @Test
    fun allLocaleStringFiles_containEveryTranslatableKeyFromDefault() {
        val resDir = findAndroidMainResDirectory()
        val reports = buildLocaleStringCompletenessReports(resDir)
        val failures = formatLocaleStringCompletenessFailures(reports)

        assertTrue(
            failures.ifEmpty { "All locale string files contain every translatable key from values/strings.xml" },
            failures.isEmpty()
        )
    }
}
