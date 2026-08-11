package me.yummydroid.app.data

internal class InMemoryAppSettingsPreferences : AppSettingsPreferences {
    val values = mutableMapOf<String, Any?>()

    override val all: Map<String, *>
        get() = values

    override fun getString(key: String, defaultValue: String?): String? {
        return values[key] as? String ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return values[key] as? Int ?: defaultValue
    }

    override fun edit(block: AppSettingsPreferences.Editor.() -> Unit) {
        InMemoryEditor(values).block()
    }
}

private class InMemoryEditor(
    private val values: MutableMap<String, Any?>,
) : AppSettingsPreferences.Editor {
    override fun putString(key: String, value: String?) {
        values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
