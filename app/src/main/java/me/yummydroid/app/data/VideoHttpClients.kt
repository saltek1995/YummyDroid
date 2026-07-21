package me.yummydroid.app.data

import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

internal fun defaultVideoResolveClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .callTimeout(14, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .withVideoTlsCompatibility()
        .build()
}

internal fun defaultVideoDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .withVideoTlsCompatibility()
        .build()
}

internal fun OkHttpClient.Builder.withVideoTlsCompatibility(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
}
