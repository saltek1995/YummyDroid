package me.yummydroid.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoSubscriptionApiMappingTest {
    @Test
    fun decodesDocumentedSubscriptionResponse() {
        val body = """
            {
              "response": [
                {
                  "anime_id": 12,
                  "title": "Anime title",
                  "poster": {"medium": "https://example.test/poster.webp"},
                  "sub": {"player": "Kodik", "player_id": 123, "dubbing": "AniLibria"}
                }
              ]
            }
        """.trimIndent()

        val response = YUMMY_ANIME_API_JSON
            .decodeFromString<ApiEnvelope<List<SubscriptionDto>>>(body)
            .response
            .mapNotNull(SubscriptionDto::toVideoSubscription)

        assertEquals(1, response.size)
        assertEquals(12L, response.single().animeId)
        assertEquals("AniLibria", response.single().dubbing)
    }

    @Test
    fun mapsOfficialServerSubscriptionContract() {
        val dto = SubscriptionDto(
            animeId = 10,
            title = "Anime",
            sub = SubscriptionDataDto(
                player = "Kodik",
                playerId = 9,
                dubbing = "AniLibria",
            ),
        )

        val subscription = dto.toVideoSubscription()

        assertEquals(10L, subscription?.animeId)
        assertEquals("Kodik", subscription?.player)
        assertEquals(9L, subscription?.playerId)
        assertEquals("AniLibria", subscription?.dubbing)
        assertEquals(0L, subscription?.videoId)
    }

    @Test
    fun rejectsSubscriptionWithoutServerIdentity() {
        assertNull(
            SubscriptionDto(
                animeId = 10,
                title = "Anime",
                sub = SubscriptionDataDto(),
            ).toVideoSubscription(),
        )
    }

    @Test
    fun subscriptionRequestUsesDocumentedEndpointAndBearerToken() {
        val request = YummyAnimeApiRequestFactory(ContentLanguage.Russian).get(
            path = "/users/42/lists/subs",
            params = emptyList(),
            authToken = "profile-token",
        )

        assertEquals("https://api.yani.tv/users/42/lists/subs", request.url.toString())
        assertEquals("Bearer profile-token", request.header("Authorization"))
    }

    @Test
    fun notificationApiRequestsAndDecodesDocumentedSubscriptionResponse() = runBlocking {
        var capturedRequest: Request? = null
        val responseBody = """
            {
              "response": [
                {
                  "anime_id": 12,
                  "title": "Anime title",
                  "poster": {"medium": "https://example.test/poster.webp"},
                  "sub": {"player": "Kodik", "player_id": 123, "dubbing": "AniLibria"}
                }
              ]
            }
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    capturedRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(responseBody.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()

        val subscriptions = YummyAnimeApiRuntime(client)
            .getVideoSubscriptions(userId = 42, token = "profile-token")

        assertEquals("/users/42/lists/subs", capturedRequest?.url?.encodedPath)
        assertEquals("Bearer profile-token", capturedRequest?.header("Authorization"))
        assertEquals("AniLibria", subscriptions.single().dubbing)
    }
}
