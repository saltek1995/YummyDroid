package me.yummydroid.app.ui

private val MissingFactValues = setOf(
    "-",
    "\u2014",
    "\u0432\u0402\u201d",
)

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized !in MissingFactValues
}
