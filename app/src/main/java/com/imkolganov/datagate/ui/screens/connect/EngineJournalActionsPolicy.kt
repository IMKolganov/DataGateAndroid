package com.imkolganov.datagate.ui.screens.connect

/** Copy / clear are only useful when the Home journal actually has lines. */
internal object EngineJournalActionsPolicy {
    fun hasCopyableContent(text: String): Boolean = text.isNotBlank()

    fun canClear(text: String): Boolean = hasCopyableContent(text)
}
