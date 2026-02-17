package com.imkolganov.datagate

import android.app.Application
import com.imkolganov.datagate.logger.CrashLogger

class DataGateApp : Application() {

    lateinit var crashLogger: CrashLogger
        private set

    override fun onCreate() {
        super.onCreate()
        crashLogger = CrashLogger(this)
        crashLogger.install()
    }
}
