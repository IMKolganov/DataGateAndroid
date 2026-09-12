package com.imkolganov.datagate.logger

/** Pure formatting shared by the file logger and the Home engine journal. */
internal object VpnDebugLogFormat {
    fun eventMessage(
        category: String,
        action: String,
        details: Map<String, Any?> = emptyMap(),
    ): String {
        val detailText = details.entries
            .filter { it.value != null && it.value.toString().isNotBlank() }
            .joinToString(" ") { (k, v) ->
                val raw = v.toString().replace('\n', ' ').replace('"', '\'')
                val clipped = if (raw.length > 240) raw.take(237) + "..." else raw
                "$k=$clipped"
            }
        return buildString {
            append("EVENT ")
            append(category)
            append('.')
            append(action)
            if (detailText.isNotEmpty()) {
                append(' ')
                append(detailText)
            }
        }
    }

    fun journalMessage(message: String, error: Throwable?): String {
        if (error == null) return message
        return "$message | ${error.javaClass.simpleName}: ${error.message ?: ""}"
    }
}
