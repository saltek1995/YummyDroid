package me.yummydroid.app.data

import kotlinx.serialization.json.Json

internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
