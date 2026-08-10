package me.yummydroid.app.data

internal fun String.normalizeSubtitleMetadataBody(): String {
    return replace("\\/", "/")
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
}
