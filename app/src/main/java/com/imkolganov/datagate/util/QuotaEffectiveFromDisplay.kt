package com.imkolganov.datagate.util

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Quota APIs often send ISO date-time at 00:00:00 when only a calendar date is meant.
 * In that case we show a localized date without a meaningless midnight time.
 */
fun formatQuotaEffectiveFromForDisplay(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return s

    val dateOnly = Regex("""^(\d{4}-\d{2}-\d{2})$""")
    val midnightIso = Regex(
        """^(\d{4}-\d{2}-\d{2})T00:00:00(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$"""
    )

    val ymd = when {
        dateOnly.matches(s) -> dateOnly.matchEntire(s)!!.groupValues[1]
        midnightIso.matches(s) -> midnightIso.matchEntire(s)!!.groupValues[1]
        else -> null
    }
    if (ymd != null) {
        return formatYmdLocalMedium(ymd) ?: s
    }

    return tryFormatIsoDateTime(s) ?: s
}

private fun formatYmdLocalMedium(ymd: String): String? {
    return try {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val date = df.parse(ymd) ?: return null
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(date)
    } catch (_: ParseException) {
        null
    }
}

private fun tryFormatIsoDateTime(s: String): String? {
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    )
    for (p in patterns) {
        try {
            val df = SimpleDateFormat(p, Locale.US).apply {
                isLenient = false
                if (p.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
            }
            val d = df.parse(s) ?: continue
            return DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            ).format(d)
        } catch (_: ParseException) {
            continue
        }
    }
    return null
}
