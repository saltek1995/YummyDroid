package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream

internal fun File.subtitleTextOrNull(): String? {
    if (!isFile || length() <= 0L) return null
    return runCatching { readText(Charsets.UTF_8) }.getOrNull()
}

internal fun File.writeVerifiedSubtitleCacheFile(text: String, mimeType: String): Boolean {
    val directory = parentFile ?: return false
    if (!directory.exists() && !directory.mkdirs()) return false
    val bytes = text.toByteArray(Charsets.UTF_8)
    val tempFile = File(directory, "$name.${System.nanoTime()}.tmp")

    return runCatching {
        FileOutputStream(tempFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(tempFile.isFile && tempFile.length() == bytes.size.toLong())
        check(tempFile.hasSubtitleCues(mimeType = mimeType))
        if (exists() && !delete()) {
            check(!exists())
        }
        if (!tempFile.renameTo(this)) {
            tempFile.copyTo(this, overwrite = true)
            check(tempFile.delete() || !tempFile.exists())
        }
        isFile &&
            length() == bytes.size.toLong() &&
            readBytes().contentEquals(bytes) &&
            hasSubtitleCues(mimeType = mimeType)
    }.getOrElse {
        runCatching { tempFile.delete() }
        false
    }
}

internal fun File.hasSubtitleCues(mimeType: String? = null): Boolean {
    return subtitleTextOrNull()?.hasSubtitleCues(mimeType = mimeType, uri = name) == true
}
