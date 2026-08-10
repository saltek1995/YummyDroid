package me.yummydroid.app.data

import java.util.Locale

enum class PosterCardSize(
    val title: String,
    val minWidthDp: Int,
) {
    Compact("Compact", 148),
    Standard("Standard", 176),
    Large("Large", 212);

    companion object {
        fun fromName(name: String): PosterCardSize? = entries.firstOrNull { it.name == name }
    }
}

enum class ContentLanguage(
    val title: String,
    val apiCode: String,
) {
    Russian("Russian", "ru"),
    English("English", "en"),
    Ukrainian("Ukrainian", "uk");

    companion object {
        fun fromName(name: String): ContentLanguage? = entries.firstOrNull { it.name == name }
    }

    val locale: Locale
        get() = Locale.forLanguageTag(apiCode)
}

data class InterfaceScale(
    val percent: Int,
) {
    val title: String
        get() = "$percent%"

    val multiplier: Float
        get() = percent / 100f

    companion object {
        val Default = InterfaceScale(DEFAULT_INTERFACE_SCALE_PERCENT)

        fun fromPercent(percent: Int): InterfaceScale {
            val clamped = percent.coerceIn(MIN_INTERFACE_SCALE_PERCENT, MAX_INTERFACE_SCALE_PERCENT)
            val stepOffset = clamped - MIN_INTERFACE_SCALE_PERCENT
            val normalizedStep = (stepOffset + INTERFACE_SCALE_STEP_PERCENT / 2) / INTERFACE_SCALE_STEP_PERCENT
            return InterfaceScale(
                (MIN_INTERFACE_SCALE_PERCENT + normalizedStep * INTERFACE_SCALE_STEP_PERCENT)
                    .coerceAtMost(MAX_INTERFACE_SCALE_PERCENT),
            )
        }

        fun fromPersistedValue(value: Any?): InterfaceScale? {
            return when (value) {
                is Int -> fromPercent(value)
                is Long -> fromPercent(value.toInt())
                is String -> fromPersistedString(value)
                else -> null
            }
        }

        private fun fromPersistedString(value: String): InterfaceScale? {
            val trimmed = value.trim()
            val percent = trimmed
                .removePrefix("Percent")
                .removeSuffix("%")
                .toIntOrNull()
                ?: return null
            return fromPercent(percent)
        }
    }
}

