package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsFocusLayoutPolicyTest {
    @Test
    fun layoutAllocatesSectionsInVisualOrder() {
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
        assertEquals(4, detailsSubscriptionFocusItemCount(true, 5, 3, true, true, true))
        assertEquals(0, detailsSubscriptionFocusItemCount(false, 5, 3, true, true, true))
        assertEquals(0, detailsSubscriptionFocusItemCount(true, 0, 3, true, true, true))
        assertEquals(0, detailsSubscriptionFocusItemCount(true, 5, 0, true, true, true))
        assertEquals(0, detailsSubscriptionFocusItemCount(true, 5, 3, false, true, true))
        assertEquals(0, detailsSubscriptionFocusItemCount(true, 5, 3, true, false, true))
    }

    @Test
    fun commentFocusMatchesAuthorizedAndReadOnlyLayouts() {
        assertEquals(0, detailsCommentsFocusItemCount(false, 5, true, true))
        assertEquals(0, detailsCommentsFocusItemCount(true, 0, false, true))
        assertEquals(1, detailsCommentsFocusItemCount(true, 5, false, false))
        assertEquals(8, detailsCommentsFocusItemCount(true, 5, true, true))
        assertEquals(6, detailsCommentsFocusItemCount(true, 5, false, true))
        assertEquals(9, detailsCommentsFocusItemCount(true, 5, true, true, hasPagingError = true))
    }
}
