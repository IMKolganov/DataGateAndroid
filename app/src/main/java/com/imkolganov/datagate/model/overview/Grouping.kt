package com.imkolganov.datagate.model.overview

enum class Grouping(val apiValue: Int, val displayName: String) {
    Days(0, "Days"),
    Hours(1, "Hours"),
    Weeks(2, "Weeks"),
    Months(3, "Months")
}