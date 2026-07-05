package com.imkolganov.datagate.i18n

import java.io.File

private val STRING_OPEN_TAG =
    Regex("""<string\s+name="([^"]+)"([^>]*)>""")

fun parseStringResourceKeys(xml: String): Set<String> =
    STRING_OPEN_TAG.findAll(xml).map { it.groupValues[1] }.toSet()

fun parseTranslatableStringResourceKeys(xml: String): Set<String> =
    STRING_OPEN_TAG.findAll(xml)
        .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
        .map { it.groupValues[1] }
        .toSet()

fun findAndroidMainResDirectory(startDir: File = File(checkNotNull(System.getProperty("user.dir")))): File {
    var dir: File? = startDir
    while (dir != null) {
        val res = File(dir, "app/src/main/res")
        if (res.isDirectory) return res
        dir = dir.parentFile
    }
    error("Could not find app/src/main/res starting from ${startDir.absolutePath}")
}

data class LocaleStringCompletenessReport(
    val localeFolder: String,
    val missingKeys: Set<String>,
    val extraKeys: Set<String>,
) {
    val isComplete: Boolean get() = missingKeys.isEmpty()
}

fun buildLocaleStringCompletenessReports(resDir: File): List<LocaleStringCompletenessReport> {
    val defaultFile = File(resDir, "values/strings.xml")
    require(defaultFile.isFile) { "Missing default strings file: ${defaultFile.absolutePath}" }

    val defaultXml = defaultFile.readText()
    val requiredKeys = parseTranslatableStringResourceKeys(defaultXml)
    val allDefaultKeys = parseStringResourceKeys(defaultXml)

    return resDir.listFiles { file ->
        file.isDirectory &&
            file.name.startsWith("values-") &&
            File(file, "strings.xml").isFile
    }
        .orEmpty()
        .sortedBy { it.name }
        .map { localeDir ->
            val localeFile = File(localeDir, "strings.xml")
            val localeKeys = if (localeFile.isFile) {
                parseStringResourceKeys(localeFile.readText())
            } else {
                emptySet()
            }
            LocaleStringCompletenessReport(
                localeFolder = localeDir.name,
                missingKeys = requiredKeys - localeKeys,
                extraKeys = localeKeys - allDefaultKeys,
            )
        }
}

fun formatLocaleStringCompletenessFailures(reports: List<LocaleStringCompletenessReport>): String {
    val incomplete = reports.filterNot { it.isComplete }
    if (incomplete.isEmpty()) return ""

    return buildString {
        appendLine("Missing translatable string keys in locale resource files:")
        for (report in incomplete) {
            appendLine("- ${report.localeFolder} (${report.missingKeys.size} missing):")
            report.missingKeys.sorted().forEach { key ->
                appendLine("    • $key")
            }
        }
    }.trimEnd()
}
