package me.yummydroid.app.data

internal interface RuntimeStreamResolver {
    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream
}
