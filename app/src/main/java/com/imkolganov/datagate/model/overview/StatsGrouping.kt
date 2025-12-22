package com.imkolganov.datagate.model.overview

enum class StatsGrouping(val apiValue: Int, val displayName: String) {
    Auto(0, "Auto"),
    Hours(1, "Hours"),
    Months(3, "Months"),
    Years(4, "Years");

    companion object {
        val all: List<StatsGrouping> =
            listOf(Auto, Hours, Months, Years)
    }
}
