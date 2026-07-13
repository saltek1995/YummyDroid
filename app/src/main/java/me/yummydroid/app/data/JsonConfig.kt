package me.yummydroid.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi
internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal inline fun <reified T> String.decodeAppJsonOrNull(): T? {
    return runCatching { AppJson.decodeFromString<T>(this) }.getOrNull()
}

internal inline fun <reified T> T.encodeAppJson(): String {
    return AppJson.encodeToString(this)
}

internal inline fun <reified T> SharedPreferences.getJsonOrNull(key: String): T? {
    return getString(key, null)?.decodeAppJsonOrNull()
}

internal inline fun <reified T> SharedPreferences.putJson(key: String, value: T) {
    edit {
        putString(key, value.encodeAppJson())
    }
}

internal inline fun <reified T> File.readJsonOrNull(): T? {
    if (!exists()) return null
    return runCatching { readText().decodeAppJsonOrNull<T>() }.getOrNull()
}

internal inline fun <reified T> File.writeJson(value: T) {
    parentFile?.mkdirs()
    writeText(value.encodeAppJson())
}
