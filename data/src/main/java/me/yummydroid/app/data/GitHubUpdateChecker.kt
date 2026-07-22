package me.yummydroid.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubUpdateChecker(
    private val owner: String = "saltek1995",
    private val repo: String = "YummyDroid",
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun latestRelease(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "YummyDroid Android")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub releases недоступны: HTTP ${response.code}")
            }
            json.decodeFromString<GitHubReleaseDto>(body).toUpdateInfo()
        }
    }

    private companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
private data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

private fun GitHubReleaseDto.toUpdateInfo(): AppUpdateInfo {
    val apkAsset = assets.firstOrNull { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) &&
            asset.browserDownloadUrl.isNotBlank()
    }
    return AppUpdateInfo(
        version = tagName.ifBlank { name },
        title = name.ifBlank { tagName },
        body = body,
        pageUrl = htmlUrl,
        apkUrl = apkAsset?.browserDownloadUrl.orEmpty(),
        publishedAt = publishedAt,
    )
}
