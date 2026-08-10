package me.yummydroid.app.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class YummySizesTest {
    @Test
    fun componentSizesKeepTheirLayoutContracts() {
        assertEquals(48.dp, YummySizes.tabHeight)
        assertEquals(40.dp, YummySizes.dialogButtonHeight)
        assertEquals(84.dp, YummySizes.dialogButtonMinWidth)
        assertEquals(104.dp, YummySizes.primaryDialogButtonMinWidth)
        assertEquals(92.dp, YummySizes.animeCardInfoHeight)
        assertEquals(42.dp, YummySizes.animeTitleHeight)
        assertEquals(18.dp, YummySizes.animeMetaHeight)
        assertEquals(86.dp, YummySizes.episodeHeight)
        assertEquals(15.dp, YummySizes.badgeIcon)
    }
}
