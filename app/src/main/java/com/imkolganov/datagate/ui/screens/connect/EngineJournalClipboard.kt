package com.imkolganov.datagate.ui.screens.connect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal const val ENGINE_JOURNAL_CLIP_LABEL = "datagate-engine-journal"

internal fun copyEngineJournalToClipboard(context: Context, text: String): Boolean {
    if (!EngineJournalActionsPolicy.hasCopyableContent(text)) return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(ENGINE_JOURNAL_CLIP_LABEL, text))
    return true
}
