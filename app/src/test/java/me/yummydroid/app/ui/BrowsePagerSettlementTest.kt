package me.yummydroid.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.BrowseSection

class BrowsePagerSettlementTest {
    private val sections = listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)

    @Test
    fun backToCatalogCannotBeOverwrittenBeforeControlledTransitionStarts() = runBlocking {
        for (source in listOf(BrowseSection.History, BrowseSection.Schedule)) {
            val page = sections.indexOf(source)
            val runtime = runtime(source, page)
            val changes = mutableListOf<BrowseSection>()

            settleBrowsePagerAlignment(
                active = true,
                alignment = PagerAlignmentState(false, page, page, 0f),
                effectiveSection = BrowseSection.Catalog,
                pagerSections = sections,
                pagerPage = 0,
                runtime = runtime,
                onBrowseSectionChange = changes::add,
            )

            assertEquals(emptyList(), changes, "Back from $source must keep Catalog selected")
        }
    }

    @Test
    fun pendingProgrammaticReturnDoesNotPublishOldPage() = runBlocking {
        val runtime = runtime(BrowseSection.Catalog, 2).apply { programmaticScrollTarget = 0 }
        val changes = mutableListOf<BrowseSection>()
        settleBrowsePagerAlignment(
            true, PagerAlignmentState(false, 2, 2, 0f), BrowseSection.Catalog,
            sections, 0, runtime, changes::add,
        )
        assertEquals(emptyList(), changes)
    }

    @Test
    fun completedSwipeStillSelectsItsDestination() = runBlocking {
        val changes = mutableListOf<BrowseSection>()
        settleBrowsePagerAlignment(
            true, PagerAlignmentState(false, 1, 1, 0f), BrowseSection.Catalog,
            sections, 0, runtime(BrowseSection.Catalog, 1), changes::add,
        )
        assertEquals(listOf(BrowseSection.History), changes)
    }

    private fun runtime(section: BrowseSection, page: Int) = BrowsePagerRuntime(
        pagerState = PagerState(currentPage = page) { sections.size },
        pageStateHolder = object : SaveableStateHolder {
            @Composable
            override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) = content()
            override fun removeState(key: Any) = Unit
        },
        initialSection = section,
    ).apply { wasAligned = true }
}
