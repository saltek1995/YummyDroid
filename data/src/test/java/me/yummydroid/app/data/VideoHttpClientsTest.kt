package me.yummydroid.app.data

import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

class VideoHttpClientsTest {
    private val factories = listOf(::defaultVideoResolveClient, ::defaultVideoDownloadClient)

    @Test
    fun defaultClientsRejectUntrustedCertificatesBeforeSendingHttp() {
        for (factory in factories) withServer("localhost") { server, _ ->
            val client = factory()
            try {
                assertFailsWith<SSLHandshakeException> { get(client, server) }
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
            } finally { client.releaseTestResources() }
        }
    }

    @Test
    fun trustingCertificateDoesNotDisableHostnameValidation() {
        for (factory in factories) withServer("wrong.example.test") { server, certificate ->
            val client = factory().trustTestCertificate(certificate)
            try {
                assertFailsWith<SSLPeerUnverifiedException> { get(client, server) }
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
            } finally { client.releaseTestResources() }
        }
    }

    @Test
    fun trustedCertificateAndMatchingHostnameAllowPlaybackRequests() {
        for (factory in factories) withServer("localhost") { server, certificate ->
            val client = factory().trustTestCertificate(certificate)
            try {
                server.enqueue(MockResponse().setBody("media payload"))
                assertEquals("media payload", get(client, server))
                assertEquals("/media", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            } finally { client.releaseTestResources() }
        }
    }

    private fun withServer(host: String, action: (MockWebServer, HeldCertificate) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(host).build()
        val identity = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(identity.sslSocketFactory(), false)
            server.start()
            action(server, certificate)
        }
    }

    private fun OkHttpClient.trustTestCertificate(certificate: HeldCertificate): OkHttpClient {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        return newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
    }

    private fun get(client: OkHttpClient, server: MockWebServer): String =
        client.newCall(Request.Builder().url(server.url("/media").newBuilder().host("localhost").build()).build())
            .execute().use { it.body!!.string() }

    private fun OkHttpClient.releaseTestResources() {
        connectionPool.evictAll()
        dispatcher.executorService.shutdownNow()
    }
}
