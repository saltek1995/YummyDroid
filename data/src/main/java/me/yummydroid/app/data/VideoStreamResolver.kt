package me.yummydroid.app.data

import android.content.Context
import okhttp3.OkHttpClient

class VideoStreamResolver(
    context: Context? = null,
    siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val runtime = VideoStreamResolveRuntime(
        context = context,
        siteDomainResolver = siteDomainResolver,
        client = client,
    )

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        return runtime.resolve(video, preferredQuality, waitForRuntimeSubtitles)
    }
}
