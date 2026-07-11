package me.yummydroid.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class SiteDomainResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    candidates: List<String> = DEFAULT_SITE_DOMAINS,
) {
    @Volatile
    private var candidates: List<String> = candidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var checkedAtMs: Long = 0L

    suspend fun activeBaseUrl(): String = withContext(Dispatchers.IO) {
        activeBaseUrlBlocking()
    }

    suspend fun orderedBaseUrlsFor(rawUrl: String): List<String> = withContext(Dispatchers.IO) {
        val active = activeBaseUrlBlocking()
        if (!rawUrl.isSiteRelativeOrKnownHost()) {
            return@withContext listOf(active)
        }
        (listOf(active) + candidates).distinct()
    }

    fun cachedOrDefaultBaseUrl(): String = cachedBaseUrl ?: candidates.first()

    fun updateCandidates(rawCandidates: List<String>) {
        val updatedCandidates = rawCandidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }
        candidates = updatedCandidates
        if (cachedBaseUrl?.let { cached -> updatedCandidates.any { it.sameOrigin(cached) } } != true) {
            cachedBaseUrl = null
            checkedAtMs = 0L
        }
    }

    fun markAvailable(baseUrl: String) {
        cachedBaseUrl = baseUrl.toRootBaseUrl()
        checkedAtMs = System.currentTimeMillis()
    }

    fun markUnavailable(baseUrl: String) {
        if (baseUrl.sameOrigin(cachedBaseUrl)) {
            cachedBaseUrl = null
            checkedAtMs = 0L
        }
    }

    fun isKnownSiteHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return host.lowercase() in (candidates + DEFAULT_SITE_DOMAINS)
            .mapNotNull { runCatching { it.toHttpUrl().host.lowercase() }.getOrNull() }
            .toSet()
    }

    private fun activeBaseUrlBlocking(): String {
        val now = System.currentTimeMillis()
        cachedBaseUrl
            ?.takeIf { now - checkedAtMs < CACHE_TTL_MS }
            ?.let { return it }

        candidates.firstOrNull(::isReachable)?.let { baseUrl ->
            cachedBaseUrl = baseUrl
            checkedAtMs = now
            return baseUrl
        }

        cachedBaseUrl = candidates.first()
        checkedAtMs = now
        return candidates.first()
    }

    private fun isReachable(baseUrl: String): Boolean {
        return request(baseUrl, "HEAD") || request(baseUrl, "GET")
    }

    private fun request(baseUrl: String, method: String): Boolean {
        val request = Request.Builder()
            .url(baseUrl)
            .method(method, null)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                response.code in 200..499
            }
        }.getOrDefault(false)
    }

    private fun String.isSiteRelativeOrKnownHost(): Boolean {
        val value = trim()
        if (value.startsWith("/")) return true
        val host = runCatching { value.toHttpUrl().host }.getOrNull()
        return isKnownSiteHost(host)
    }

    private fun String.sameOrigin(other: String?): Boolean {
        if (other.isNullOrBlank()) return false
        val first = toRootBaseUrl().trimEnd('/')
        val second = other.toRootBaseUrl().trimEnd('/')
        return first.equals(second, ignoreCase = true)
    }

    private fun String.toRootBaseUrl(): String {
        return runCatching { toHttpUrl().newBuilder().encodedPath("/").query(null).build().toString() }
            .getOrDefault(this)
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

        val DEFAULT_SITE_DOMAINS: List<String> = listOf(
            "https://old.yummyani.me/",
            "https://ru.yummyani.me/",
            "https://yummyani.me/",
            "https://yummy-ani.me/",
            "https://old.yummy-ani.me/",
            "https://yummyani.meme/",
            "https://site.yummyani.me/",
            "https://en.yummyani.me/",
            "https://uk.yummyani.me/",
            "https://yummy-anime.ru/",
        )
    }
}

internal fun normalizeSiteBaseUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return runCatching {
        withScheme.toHttpUrl()
            .newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }.getOrNull()
}

internal fun Iterable<String>.normalizedSiteBaseUrls(): List<String> {
    return mapNotNull(::normalizeSiteBaseUrl)
        .distinctBy { it.trimEnd('/').lowercase() }
}
