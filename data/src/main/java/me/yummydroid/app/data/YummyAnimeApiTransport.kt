package me.yummydroid.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal val defaultYummyAnimeApiClient: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

internal class YummyAnimeApiTransport(
    client: OkHttpClient,
    initialContentLanguage: ContentLanguage,
) {
    @PublishedApi
    internal val requests = YummyAnimeApiRequestFactory(initialContentLanguage)

    @PublishedApi
    internal val responses = YummyAnimeApiResponseReader(client)

    val locale
        get() = requests.locale

    fun updateContentLanguage(language: ContentLanguage) = requests.updateContentLanguage(language)

    fun submitCaptchaResponse(response: String) = requests.submitCaptchaResponse(response)

    suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        authToken: String? = null,
    ): T = read { requests.get(path, params, authToken) }

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = read {
        requests.write(ApiWriteMethod.Post, path, authToken, prepareBodyBeforeRequest = true) {
            requests.withCaptcha(body)
        }
    }

    suspend fun postEmptySuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Post, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = read {
        requests.write(ApiWriteMethod.Put, path, authToken, prepareBodyBeforeRequest = true) {
            requests.withCaptcha(body)
        }
    }

    suspend fun putEmptySuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Put, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified T> delete(path: String, authToken: String? = null): T = read {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend fun deleteSuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified B> deleteSuccess(
        path: String,
        body: B,
        authToken: String? = null,
    ): Boolean = success {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.withCaptcha(body) }
    }

    @PublishedApi
    internal suspend inline fun <reified T> read(crossinline request: () -> Request): T {
        return withContext(Dispatchers.IO) { responses.read(request()) }
    }

    @PublishedApi
    internal suspend fun success(request: () -> Request): Boolean {
        return withContext(Dispatchers.IO) { responses.isSuccessful(request()) }
    }
}
