package com.sirterrific.tubetamer.ui

import com.sirterrific.tubetamer.api.ApiResult
import com.sirterrific.tubetamer.api.CatalogPage
import com.sirterrific.tubetamer.api.VideoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class HomeHelpersTest {
    @Test fun durations() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("4:05", formatDuration(245))
        assertEquals("1:02:03", formatDuration(3723))
    }

    @Test fun resolveKeepsServerSubPath() {
        assertEquals("http://nas/tubetamer/thumb/abc", resolve("http://nas/tubetamer/", "/thumb/abc"))
        assertEquals("http://192.168.1.10:8080/thumb/abc", resolve("http://192.168.1.10:8080/", "/thumb/abc"))
        assertNull(resolve("http://nas/", ""))
    }

    @Test fun progressIsClamped() {
        assertEquals(0f, progressFraction(10, 0))
        assertEquals(0.5f, progressFraction(50, 100))
        assertEquals(1f, progressFraction(500, 100))
    }

    private fun card(id: String) = VideoCard(videoId = id)

    @Test fun appendSkipsDuplicatesAndStopsSpinner() {
        val row = VideoRow("edu", listOf(card("a"), card("b")), total = 4, hasMore = true, loadingMore = true)
        val next = row.appended(ApiResult.Ok(CatalogPage(listOf(card("b"), card("c")), total = 3, hasMore = false)))
        assertEquals(listOf("a", "b", "c"), next.videos.map { it.videoId })
        assertFalse(next.hasMore)
        assertFalse(next.loadingMore)
    }

    @Test fun failedPageKeepsRowAndAllowsRetry() {
        val row = VideoRow("edu", listOf(card("a")), total = 4, hasMore = true, loadingMore = true)
        val next = row.appended(ApiResult.NetworkError(IOException("down")))
        assertEquals(1, next.videos.size)
        assertEquals(true, next.hasMore)
        assertFalse(next.loadingMore)
    }
}
