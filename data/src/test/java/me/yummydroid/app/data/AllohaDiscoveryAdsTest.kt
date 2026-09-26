package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllohaDiscoveryAdsTest {
    private val page = "https://provider.test/player?id=synthetic"

    @Test fun blocksOnlyTheKnownAdvertisingResources() {
        assertTrue(isAllohaAdvertisingRequest("https://provider.test/js/rmp-vast.min.js?v=2.6", page))
        assertTrue(isAllohaAdvertisingRequest("https://imasdk.googleapis.com/cekh8i", page))
        assertTrue(isAllohaAdvertisingRequest("https://pc.alloviewroll.com/lists.php", page))
        for (url in listOf("https://provider.test/events", "https://provider.test/stat",
            "https://provider.test/build/app.js", "https://provider.test/bnsi/video/1",
            "https://cdn.test/video.m4s", "https://other.test/js/rmp-vast.min.js",
            "https://provider.test:444/js/rmp-vast.min.js", "https://imasdk.googleapis.com/other",
            "https://pc.alloviewroll.com.example.test/lists.php", "not a URL")) {
            assertFalse(isAllohaAdvertisingRequest(url, page), url)
        }
    }
}
