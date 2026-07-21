package me.yummydroid.app.data

internal fun AppUpdateInfo.isNewerThanVersion(currentVersion: String): Boolean {
    val latest = normalizedVersion.versionParts()
    val current = currentVersion.versionParts()
    val maxSize = maxOf(latest.size, current.size)
    repeat(maxSize) { index ->
        val left = latest.getOrElse(index) { 0 }
        val right = current.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun String.versionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
        .ifEmpty { listOf(0) }
}

