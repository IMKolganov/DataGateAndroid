package com.imkolganov.datagate.vpn

enum class IpListCoverageMode(val storageValue: String) {
    FAST("fast"),
    FULL("full");

    companion object {
        fun fromStorageValue(value: String?): IpListCoverageMode =
            entries.firstOrNull { it.storageValue == value } ?: FULL
    }
}
