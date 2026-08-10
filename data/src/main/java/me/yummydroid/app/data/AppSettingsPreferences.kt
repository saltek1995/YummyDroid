package me.yummydroid.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

internal interface AppSettingsPreferences {
    val all: Map<String, *>

    fun getString(key: String, defaultValue: String?): String?

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getInt(key: String, defaultValue: Int): Int

    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String?)

        fun putBoolean(key: String, value: Boolean)

        fun putInt(key: String, value: Int)

        fun remove(key: String)
    }
}

internal class SharedPreferencesAppSettingsPreferences(
    private val preferences: SharedPreferences,
) : AppSettingsPreferences {
    override val all: Map<String, *>
        get() = preferences.all

    override fun getString(key: String, defaultValue: String?): String? {
        return preferences.getString(key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return preferences.getInt(key, defaultValue)
    }

    override fun edit(block: AppSettingsPreferences.Editor.() -> Unit) {
        preferences.edit {
            SharedPreferencesEditor(this).block()
        }
    }
}

private class SharedPreferencesEditor(
    private val editor: SharedPreferences.Editor,
) : AppSettingsPreferences.Editor {
    override fun putString(key: String, value: String?) {
        editor.putString(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        editor.putBoolean(key, value)
    }

    override fun putInt(key: String, value: Int) {
        editor.putInt(key, value)
    }

    override fun remove(key: String) {
        editor.remove(key)
    }
}
