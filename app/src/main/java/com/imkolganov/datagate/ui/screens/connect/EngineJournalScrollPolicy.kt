package com.imkolganov.datagate.ui.screens.connect

internal object EngineJournalScrollPolicy {
    const val STICK_THRESHOLD_PX = 32

    fun shouldStickToBottom(
        scrollValue: Int,
        maxValue: Int,
        thresholdPx: Int = STICK_THRESHOLD_PX,
    ): Boolean = maxValue <= 0 || scrollValue >= maxValue - thresholdPx
}
