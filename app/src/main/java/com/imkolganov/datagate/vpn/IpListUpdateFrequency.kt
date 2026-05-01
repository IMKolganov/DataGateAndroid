package com.imkolganov.datagate.vpn

enum class IpListUpdateFrequency(val storageValue: String, val hours: Int) {
    SIX_HOURS("6h", 6),
    DAILY("24h", 24),
    WEEKLY("7d", 24 * 7),
    MANUAL("manual", 0);

    companion object {
        fun fromStorageValue(value: String?): IpListUpdateFrequency =
            entries.firstOrNull { it.storageValue == value } ?: DAILY
    }
}
