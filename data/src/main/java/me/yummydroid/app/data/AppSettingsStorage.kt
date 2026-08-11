package me.yummydroid.app.data

import android.content.Context

class AppSettingsStorage internal constructor(
    private val prefs: AppSettingsPreferences,
) {
    constructor(context: Context) : this(
        SharedPreferencesAppSettingsPreferences(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun read(): AppSettings = prefs.readAppSettings()

    fun save(settings: AppSettings) {
        prefs.saveAppSettings(settings)
    }

    fun readInterfaceScale(): InterfaceScale = prefs.readInterfaceScalePreference()

    fun saveInterfaceScale(interfaceScale: InterfaceScale) {
        prefs.saveInterfaceScalePreference(interfaceScale)
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_settings"
    }
}
