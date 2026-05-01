package com.imkolganov.datagate

import android.app.Application
import com.imkolganov.datagate.logger.CrashLogger
import com.imkolganov.datagate.logger.CrashUploadWorkScheduler
import com.imkolganov.datagate.ui.theme.LanguagePreferenceStore

class DataGateApp : Application() {

    lateinit var crashLogger: CrashLogger
        private set

    override fun onCreate() {
        super.onCreate()
        LanguagePreferenceStore.apply(this)
        crashLogger = CrashLogger(this)
        crashLogger.install()
        CrashUploadWorkScheduler.enqueue(this)
    }
}
