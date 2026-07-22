package me.yummydroid.app.data

import okhttp3.Headers

internal fun Map<String, String>.toOkHttpHeaders(): Headers {
    return Headers.Builder().also { builder ->
        forEach { (name, value) -> builder.set(name, value) }
    }.build()
}
