package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsScreenTest {
    @Test
    fun focusLayoutAllocatesSectionsInVisualOrder() {
        val layout = buildDetailsFocusLayout(
            DetailsFocusCounts(
                screenshots = 2,
                relatedAnime = 3,
                episodes = 24,
                subscriptions = 4,
                recommendations = 5,
                comments = 6,
            ),
        )

        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE, layout.offset(DetailsFocusBlock.Screenshots))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 2, layout.offset(DetailsFocusBlock.RelatedAnime))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 5, layout.offset(DetailsFocusBlock.Episodes))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 29, layout.offset(DetailsFocusBlock.Subscriptions))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 33, layout.offset(DetailsFocusBlock.Recommendations))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 38, layout.offset(DetailsFocusBlock.Comments))
        assertEquals(DETAILS_HERO_FOCUS_GRAPH_SIZE + 44, layout.size)
    }

    @Test
    fun expandableSectionReservesToggleAndExpandedItems() {
        assertEquals(0, detailsExpandedListFocusCount(itemCount = 0, expanded = true))
        assertEquals(1, detailsExpandedListFocusCount(itemCount = 3, expanded = false))
        assertEquals(4, detailsExpandedListFocusCount(itemCount = 3, expanded = true))
    }

    @Test
    fun subscriptionFocusRequiresEveryVisiblePrerequisite() {
        assertEquals(
            4,
            detailsSubscriptionFocusItemCount(
                isAuthorized = true,
                videoCount = 5,
                voiceGroupCount = 3,
                allowSubscriptions = true,
                extrasReady = true,
                expanded = true,
            ),
        )
        assertEquals(
            0,
            detailsSubscriptionFocusItemCount(
                isAuthorized = false,
                videoCount = 5,
                voiceGroupCount = 3,
                allowSubscriptions = true,
                extrasReady = true,
                expanded = true,
            ),
        )
    }

    @Test
    fun commentFocusMatchesAuthorizedAndReadOnlyLayouts() {
        assertEquals(0, detailsCommentsFocusItemCount(false, 5, true, true))
        assertEquals(0, detailsCommentsFocusItemCount(true, 0, false, true))
        assertEquals(1, detailsCommentsFocusItemCount(true, 5, false, false))
        assertEquals(3, detailsCommentsFocusItemCount(true, 5, true, true))
        assertEquals(6, detailsCommentsFocusItemCount(true, 5, false, true))
    }

    @Test
    fun bringIntoViewKeepsFocusedContentInsideGuardedEdges() {
        assertEquals(-36f, DetailsBringIntoViewSpec.calculateScrollDistance(20f, 100f, 1000f))
        assertEquals(56f, DetailsBringIntoViewSpec.calculateScrollDistance(900f, 100f, 1000f))
        assertEquals(0f, DetailsBringIntoViewSpec.calculateScrollDistance(100f, 100f, 1000f))
    }
}
