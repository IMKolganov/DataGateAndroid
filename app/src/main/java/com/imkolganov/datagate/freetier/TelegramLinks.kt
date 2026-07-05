package com.imkolganov.datagate.freetier

fun telegramHandleFromRequiredChannel(requiredChannel: String?): String? =
    requiredChannel
        ?.trim()
        ?.removePrefix("@")
        ?.takeIf { it.isNotEmpty() }

fun telegramChannelUrl(
    requiredChannel: String?,
    defaultHandle: String = DEFAULT_REQUIRED_TELEGRAM_CHANNEL_HANDLE,
): String {
    val handle = telegramHandleFromRequiredChannel(requiredChannel) ?: defaultHandle
    return "https://t.me/$handle"
}
