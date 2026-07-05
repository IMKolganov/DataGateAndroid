package com.imkolganov.datagate.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StringResourceCatalogTest {

    @Test
    fun parseTranslatableStringResourceKeys_skipsNonTranslatableEntries() {
        val xml = """
            <resources>
                <string name="visible">Hello</string>
                <string name="hidden" translatable="false">URL</string>
            </resources>
        """.trimIndent()

        assertEquals(setOf("visible"), parseTranslatableStringResourceKeys(xml))
        assertEquals(setOf("visible", "hidden"), parseStringResourceKeys(xml))
    }

    @Test
    fun buildLocaleStringCompletenessReports_detectsMissingAndExtraKeys() {
        val tempRoot = Files.createTempDirectory("strings-test-").toFile()
        tempRoot.deleteOnExit()
        val resDir = File(tempRoot, "app/src/main/res").apply { mkdirs() }

        File(resDir, "values").mkdirs()
        File(resDir, "values/strings.xml").writeText(
            """
            <resources>
                <string name="app_name">DataGate</string>
                <string name="url" translatable="false">https://example.test</string>
                <string name="greeting">Hello</string>
            </resources>
            """.trimIndent()
        )

        File(resDir, "values-ru").mkdirs()
        File(resDir, "values-ru/strings.xml").writeText(
            """
            <resources>
                <string name="app_name">DataGate</string>
                <string name="greeting">Привет</string>
                <string name="orphan">Extra</string>
            </resources>
            """.trimIndent()
        )

        File(resDir, "values-de").mkdirs()
        File(resDir, "values-de/strings.xml").writeText(
            """
            <resources>
                <string name="app_name">DataGate</string>
            </resources>
            """.trimIndent()
        )

        val reports = buildLocaleStringCompletenessReports(resDir)
        val ru = reports.single { it.localeFolder == "values-ru" }
        val de = reports.single { it.localeFolder == "values-de" }

        assertTrue(ru.isComplete)
        assertEquals(setOf("orphan"), ru.extraKeys)
        assertEquals(setOf("greeting"), de.missingKeys)
    }
}
