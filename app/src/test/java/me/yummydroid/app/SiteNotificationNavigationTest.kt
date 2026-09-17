package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.SiteNotification

class SiteNotificationNavigationTest {
    @Test
    fun pendingAliasSurvivesContextReloadUntilCanonicalPublication() {
        val pending = PendingAnimeOpenTarget()
        pending.begin(AnimeOpenTarget(0, "alias-without-id"))
        val request = pending.current
        assertEquals("alias-without-id", pending.aliasFor(0, AppRoute.Details(0)))
        // A profile/context reload uses the same visible ID without beginning a new open.
        assertEquals("alias-without-id", pending.aliasFor(0, AppRoute.Details(0)))
        assertNull(pending.aliasFor(0, AppRoute.Home))
        assertNull(pending.aliasFor(12, AppRoute.Details(12)))
        assertNull(pending.aliasFor(0, AppRoute.Details(12)))
        pending.complete(request)
        assertNull(pending.current)
        assertNull(pending.aliasFor(12, AppRoute.Details(12)))
    }

    @Test
    fun oldCompletionCannotClearNewAliasEvenWithSamePlaceholderId() {
        val pending = PendingAnimeOpenTarget()
        pending.begin(AnimeOpenTarget(0, "old-alias"))
        val old = pending.current
        pending.begin(AnimeOpenTarget(0, "new-alias"))
        pending.complete(old)
        assertEquals("new-alias", pending.aliasFor(0, AppRoute.Details(0)))
        // A cache hit/new numeric open replaces and completes the current pending target.
        pending.begin(AnimeOpenTarget(42))
        assertNull(pending.aliasFor(0, AppRoute.Details(0)))
        pending.complete(pending.current)
        assertNull(pending.current)
    }

    @Test
    fun animeTargetUsesFullCatalogAliasInsteadOfTrailingNumber() {
        val notification = notification(
            clickUrl = "https://ru.yummyani.me/catalog/item/re-zero-zhizn-s-nulya-v-alternativnom-mire-4",
            objectId = 5500,
        )

        assertEquals(
            AnimeOpenTarget(
                animeId = 5500,
                animeAlias = "re-zero-zhizn-s-nulya-v-alternativnom-mire-4",
            ),
            notification.animeTargetForOpen(),
        )
    }

    @Test
    fun animeTargetUsesCatalogAliasWithoutObjectId() {
        val notification = notification(
            clickUrl = "/catalog/item/kobayashi-i-ee-gornichnaya-drakon",
            objectId = 0,
        )

        assertEquals(
            AnimeOpenTarget(
                animeId = 0,
                animeAlias = "kobayashi-i-ee-gornichnaya-drakon",
            ),
            notification.animeTargetForOpen(),
        )
    }

    @Test
    fun animeTargetFallsBackToObjectIdForNonCatalogLink() {
        val notification = notification(
            clickUrl = "https://ru.yummyani.me/profile/notifications",
            objectId = 5500,
        )

        assertEquals(AnimeOpenTarget(animeId = 5500), notification.animeTargetForOpen())
    }

    @Test
    fun animeTargetReturnsNullWithoutKnownTarget() {
        val notification = notification(
            clickUrl = "https://ru.yummyani.me/profile/notifications",
            objectId = 0,
        )

        assertNull(notification.animeTargetForOpen())
    }

    private fun notification(
        clickUrl: String,
        objectId: Long,
    ): SiteNotification {
        return SiteNotification(
            id = 1,
            title = "New episode",
            text = "",
            clickUrl = clickUrl,
            type = "anime_episode",
            subType = "new_episode",
            objectId = objectId,
            dateSeconds = 1,
            viewed = false,
        )
    }
}
