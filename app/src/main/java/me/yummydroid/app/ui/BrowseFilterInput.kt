package me.yummydroid.app.ui

internal fun Number?.filterText(): String = when (this) {
    null -> ""
    is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
    else -> toString()
}

internal fun integerInput(value: String): String = value.filter(Char::isDigit).take(5)

internal fun decimalInput(value: String): String {
    val builder = StringBuilder()
    var dotSeen = false
    value.replace(',', '.').forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString().take(4)
}

internal fun String.yearFilterValue(): Int? =
    toIntOrNull()?.takeIf { value -> value in 1900..2100 }

internal fun String.episodeFilterValue(): Int? =
    toIntOrNull()?.takeIf { value -> value in 0..10000 }

internal fun String.ratingFilterValue(): Double? =
    toDoubleOrNull()?.takeIf { value -> value in 0.0..10.0 }
