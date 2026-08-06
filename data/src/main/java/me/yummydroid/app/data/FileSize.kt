package me.yummydroid.app.data

import java.io.File

fun File.totalSizeBytes(): Long {
    return runCatching {
        when {
            !exists() -> 0L
            isFile -> length().coerceAtLeast(0L)
            isDirectory -> listFiles().orEmpty().sumOf { child -> child.totalSizeBytes() }
            else -> 0L
        }
    }.getOrDefault(0L)
}
