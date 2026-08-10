package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualGridNavigationScenariosTest {
    @Test
    fun visualHorizontalTargetUsesSameVisualRow() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 100f, right = 100f, bottom = 180f),
            focusBounds(index = 1, left = 120f, top = 0f, right = 220f, bottom = 80f),
            focusBounds(index = 2, left = 120f, top = 110f, right = 220f, bottom = 190f),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Right))
    }

    @Test
    fun visualHorizontalTargetRejectsRowsWithoutVerticalOverlap() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 100f, right = 100f, bottom = 180f),
            focusBounds(index = 1, left = 120f, top = 0f, right = 220f, bottom = 80f),
        )

        assertNull(visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Right))
    }

    @Test
    fun strictHorizontalTargetRequiresOverlapButLooseCanUseNearestLayer() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 220f, right = 150f, bottom = 268f),
            focusBounds(index = 1, left = 180f, top = 20f, right = 420f, bottom = 70f),
            focusBounds(index = 2, left = 180f, top = 170f, right = 420f, bottom = 218f),
        )

        assertNull(
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = false,
            ),
        )
        assertEquals(
            0,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 2,
                direction = VisualGridDirection.Left,
                allowLoosePerpendicularMatch = true,
            ),
        )
        assertEquals(
            2,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun visualVerticalTargetUsesSameVisualColumn() {
        val bounds = listOf(
            focusBounds(index = 0, left = 100f, top = 0f, right = 180f, bottom = 80f),
            focusBounds(index = 1, left = 0f, top = 100f, right = 80f, bottom = 180f),
            focusBounds(index = 2, left = 110f, top = 100f, right = 190f, bottom = 180f),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Down))
    }

    @Test
    fun verticalNavigationDoesNotSkipNearestRowWithoutColumnOverlap() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 0f, right = 80f, bottom = 80f),
            focusBounds(index = 1, left = 140f, top = 100f, right = 220f, bottom = 180f),
            focusBounds(index = 2, left = 0f, top = 220f, right = 80f, bottom = 300f),
        )

        assertEquals(1, visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Down))
    }

    @Test
    fun verticalUpNavigationDoesNotSkipNearestRowWithoutColumnOverlap() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 0f, right = 80f, bottom = 80f),
            focusBounds(index = 1, left = 140f, top = 120f, right = 220f, bottom = 200f),
            focusBounds(index = 2, left = 0f, top = 240f, right = 80f, bottom = 320f),
        )

        assertEquals(1, visualFocusDirectionalTarget(bounds, 2, VisualGridDirection.Up))
    }

    @Test
    fun looseHeroVerticalTargetUsesNearestVisualLayer() {
        val bounds = listOf(
            focusBounds(index = 0, left = 140f, top = 0f, right = 220f, bottom = 80f),
            focusBounds(index = 1, left = 0f, top = 120f, right = 100f, bottom = 200f),
            focusBounds(index = 2, left = 120f, top = 120f, right = 240f, bottom = 200f),
        )

        assertEquals(
            2,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Down,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun crossBlockNavigationEntersTargetBlockFirstItem() {
        val bounds = listOf(
            focusBounds(
                index = 1,
                left = 0f,
                top = 0f,
                right = 80f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 2,
                left = 100f,
                top = 0f,
                right = 180f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 10,
                left = 0f,
                top = 96f,
                right = 80f,
                bottom = 144f,
                blockKey = "screenshots",
                blockEntryIndex = 10,
            ),
            focusBounds(
                index = 11,
                left = 100f,
                top = 96f,
                right = 180f,
                bottom = 144f,
                blockKey = "screenshots",
                blockEntryIndex = 10,
            ),
        )

        assertEquals(
            10,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 2,
                direction = VisualGridDirection.Down,
            ),
        )
        assertEquals(
            1,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 11,
                direction = VisualGridDirection.Up,
            ),
        )
    }

    @Test
    fun sameBlockNavigationKeepsVisualTarget() {
        val bounds = listOf(
            focusBounds(
                index = 1,
                left = 0f,
                top = 0f,
                right = 80f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 2,
                left = 100f,
                top = 0f,
                right = 180f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 1, VisualGridDirection.Right))
    }

    @Test
    fun verticalNavigationIgnoresSideElementsThatOverlapCurrentRow() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 0f,
                top = 0f,
                right = 220f,
                bottom = 80f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 24,
                left = 360f,
                top = 160f,
                right = 440f,
                bottom = 240f,
                blockKey = "marks",
                blockEntryIndex = 24,
            ),
            focusBounds(
                index = 25,
                left = 460f,
                top = 160f,
                right = 540f,
                bottom = 240f,
                blockKey = "marks",
                blockEntryIndex = 24,
            ),
            focusBounds(
                index = 80,
                left = 0f,
                top = 120f,
                right = 320f,
                bottom = 300f,
                blockKey = "screenshots",
                blockEntryIndex = 80,
            ),
        )

        assertEquals(
            0,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 80,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun horizontalCrossBlockNavigationKeepsActualVisualTarget() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 16f,
                top = 140f,
                right = 120f,
                bottom = 184f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 2,
                left = 224f,
                top = 140f,
                right = 384f,
                bottom = 184f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 4,
                left = 660f,
                top = 8f,
                right = 924f,
                bottom = 408f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertEquals(
            2,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 4,
                direction = VisualGridDirection.Left,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun rightFromHeroFactCanReachPoster() {
        val bounds = listOf(
            focusBounds(
                index = 32,
                left = 224f,
                top = 224f,
                right = 632f,
                bottom = 258f,
                blockKey = "facts",
                blockEntryIndex = 32,
            ),
            focusBounds(
                index = 4,
                left = 660f,
                top = 8f,
                right = 924f,
                bottom = 408f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertEquals(
            4,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 32,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun looseHorizontalNavigationCanReachTallSideBlockWithoutRowOverlap() {
        val bounds = listOf(
            focusBounds(
                index = 40,
                left = 224f,
                top = 420f,
                right = 360f,
                bottom = 464f,
                blockKey = "facts",
                blockEntryIndex = 40,
            ),
            focusBounds(
                index = 4,
                left = 660f,
                top = 8f,
                right = 924f,
                bottom = 408f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertNull(
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 40,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = false,
            ),
        )
        assertEquals(
            4,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 40,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun posterLeftUsesClosestVisualLayerInsteadOfFirstAction() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 16f,
                top = 140f,
                right = 120f,
                bottom = 184f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 40,
                left = 224f,
                top = 224f,
                right = 360f,
                bottom = 268f,
                blockKey = "facts",
                blockEntryIndex = 40,
            ),
            focusBounds(
                index = 4,
                left = 660f,
                top = 8f,
                right = 924f,
                bottom = 408f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertEquals(
            40,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 4,
                direction = VisualGridDirection.Left,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun verticalNavigationDoesNotSkipNearestVisualRowForBlockOrder() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 0f,
                top = 0f,
                right = 180f,
                bottom = 44f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 41,
                left = 220f,
                top = 140f,
                right = 360f,
                bottom = 184f,
                blockKey = "facts",
                blockEntryIndex = 41,
            ),
            focusBounds(
                index = 80,
                left = 220f,
                top = 240f,
                right = 620f,
                bottom = 420f,
                blockKey = "screenshots",
                blockEntryIndex = 80,
            ),
        )

        assertEquals(
            41,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 80,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun verticalNavigationDoesNotJumpToSideBlockWhenDirectBlockIsCloserOverall() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 40f,
                top = 220f,
                right = 260f,
                bottom = 280f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 24,
                left = 700f,
                top = 300f,
                right = 780f,
                bottom = 360f,
                blockKey = "marks",
                blockEntryIndex = 24,
            ),
            focusBounds(
                index = 80,
                left = 32f,
                top = 420f,
                right = 332f,
                bottom = 580f,
                blockKey = "screenshots",
                blockEntryIndex = 80,
            ),
        )

        assertEquals(
            0,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 80,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun heroVerticalNavigationKeepsClosestItemInsteadOfBlockEntry() {
        val bounds = listOf(
            focusBounds(
                index = 2,
                left = 468f,
                top = 120f,
                right = 784f,
                bottom = 164f,
                blockKey = "actions",
                blockEntryIndex = 2,
            ),
            focusBounds(
                index = 32,
                left = 468f,
                top = 204f,
                right = 614f,
                bottom = 248f,
                blockKey = "facts",
                blockEntryIndex = 32,
            ),
            focusBounds(
                index = 33,
                left = 626f,
                top = 204f,
                right = 795f,
                bottom = 248f,
                blockKey = "facts",
                blockEntryIndex = 33,
            ),
        )

        assertEquals(
            33,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 2,
                direction = VisualGridDirection.Down,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun visualNavigationIgnoresZeroSizedBounds() {
        val bounds = listOf(
            focusBounds(
                index = 3,
                left = 48f,
                top = 268f,
                right = 179f,
                bottom = 340f,
                blockKey = "stats",
                blockEntryIndex = 3,
            ),
            focusBounds(
                index = 200,
                left = 0f,
                top = 0f,
                right = 0f,
                bottom = 0f,
                blockKey = "episodes",
                blockEntryIndex = 200,
            ),
        )

        assertNull(
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 3,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun upFromHeroRatingCanEnterLargePosterThatStartsAboveIt() {
        val bounds = listOf(
            focusBounds(
                index = 3,
                left = 16f,
                top = 154f,
                right = 84f,
                bottom = 190f,
                blockKey = "stats",
                blockEntryIndex = 3,
            ),
            focusBounds(
                index = 4,
                left = 510f,
                top = 24f,
                right = 712f,
                bottom = 280f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertEquals(
            4,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 3,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun upFromHeroRatingUsesPosterOnActualTvBounds() {
        val bounds = listOf(
            focusBounds(
                index = 3,
                left = 48f,
                top = 208f,
                right = 179f,
                bottom = 304f,
                blockKey = "stats",
                blockEntryIndex = 3,
            ),
            focusBounds(
                index = 4,
                left = 1344f,
                top = 44f,
                right = 1872f,
                bottom = 836f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
        )

        assertEquals(
            4,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 3,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun upFromHeroFactsStillPrefersCloserActionRowOverSpanningPoster() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 48f,
                top = 184f,
                right = 268f,
                bottom = 224f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 4,
                left = 510f,
                top = 24f,
                right = 712f,
                bottom = 280f,
                blockKey = "poster",
                blockEntryIndex = 4,
            ),
            focusBounds(
                index = 32,
                left = 180f,
                top = 308f,
                right = 326f,
                bottom = 404f,
                blockKey = "facts",
                blockEntryIndex = 32,
            ),
        )

        assertEquals(
            0,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 32,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun upFromCommentsEntersFirstRecommendationCard() {
        val bounds = listOf(
            focusBounds(
                index = 260,
                left = 0f,
                top = 0f,
                right = 80f,
                bottom = 80f,
                blockKey = "recommendations",
                blockEntryIndex = 260,
            ),
            focusBounds(
                index = 261,
                left = 100f,
                top = 0f,
                right = 180f,
                bottom = 80f,
                blockKey = "recommendations",
                blockEntryIndex = 260,
            ),
            focusBounds(
                index = 340,
                left = 100f,
                top = 120f,
                right = 180f,
                bottom = 200f,
                blockKey = "comments",
                blockEntryIndex = 340,
            ),
        )

        assertEquals(
            260,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 340,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun upFromRecommendationsEntersFirstExpandedSubscriptionItem() {
        val bounds = listOf(
            focusBounds(
                index = 240,
                left = 48f,
                top = 324f,
                right = 1872f,
                bottom = 440f,
                blockKey = "subscriptions",
                blockEntryIndex = 240,
            ),
            focusBounds(
                index = 241,
                left = 68f,
                top = 474f,
                right = 180f,
                bottom = 516f,
                blockKey = "subscriptions",
                blockEntryIndex = 241,
            ),
            focusBounds(
                index = 242,
                left = 214f,
                top = 474f,
                right = 510f,
                bottom = 516f,
                blockKey = "subscriptions",
                blockEntryIndex = 241,
            ),
            focusBounds(
                index = 260,
                left = 48f,
                top = 764f,
                right = 392f,
                bottom = 1080f,
                blockKey = "recommendations",
                blockEntryIndex = 260,
            ),
        )

        assertEquals(
            241,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 260,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    private fun focusBounds(
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        blockKey: Any? = null,
        blockEntryIndex: Int = index,
    ) = VisualFocusBounds(
        index = index,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
    )
}
