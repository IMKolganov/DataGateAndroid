package com.imkolganov.datagate.model.overview

enum class StatsGrouping(val apiValue: Int) {
    Auto(0),
    Hours(1),
    Months(3),
    Years(4);

    companion object {
        val all: List<StatsGrouping> = listOf(Auto, Hours, Months, Years)
    }
}
