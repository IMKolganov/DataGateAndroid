package com.imkolganov.datagate.model.overview

enum class Metric {
    ActiveClients,
    TrafficTotal,
    TrafficIn,
    TrafficOut;

    companion object {
        val all: List<Metric> = values().toList()
    }
}
