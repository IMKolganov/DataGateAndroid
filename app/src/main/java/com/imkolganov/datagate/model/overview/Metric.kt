package com.imkolganov.datagate.model.overview

enum class Metric(val displayName: String) {
    ActiveClients("Active clients"),
    TrafficTotal("Traffic total"),
    TrafficIn("Traffic in"),
    TrafficOut("Traffic out");

    companion object {
        val all: List<Metric> = values().toList()
    }
}
