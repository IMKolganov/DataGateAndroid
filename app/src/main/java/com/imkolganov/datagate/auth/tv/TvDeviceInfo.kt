package com.imkolganov.datagate.auth.tv

import android.content.Context
import android.os.Build
import android.provider.Settings

object TvDeviceInfo {
    const val CLIENT_ANDROID_TV = "android-tv"

    fun deviceName(context: Context): String {
        val fromSettings = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        if (!fromSettings.isNullOrBlank()) return fromSettings

        val model = Build.MODEL?.trim().orEmpty()
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        return when {
            model.isNotEmpty() && manufacturer.isNotEmpty() &&
                !model.startsWith(manufacturer, ignoreCase = true) ->
                "$manufacturer $model"
            model.isNotEmpty() -> model
            manufacturer.isNotEmpty() -> manufacturer
            else -> "Android TV"
        }
    }
}
