package com.ljyh.mei.data.model.eapi

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class HomeFeedPaginationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun block(code: String) = HomePageResourceShow.Data.Block(positionCode = code)

    @Test fun appendPreservesOrderDeduplicatesAndStopsOnStaleCursor() {
        val first = HomePageResourceShow.Data(listOf(block("a"), block("b")), cursor = 10, hasMore = true)
        val next = first.append(HomePageResourceShow.Data(listOf(block("b"), block("c")), cursor = 17, hasMore = true))
        assertEquals(listOf("a", "b", "c"), next.blocks.map { it.positionCode })
        assertEquals(listOf("a", "b", "c"), next.loadedPositionCodes)
        assertTrue(next.hasMore)
        assertFalse(next.append(next.copy(hasMore = true)).hasMore)
    }

    @Test fun acceptsNativeBlockWithNullDsl() {
        val response = json.decodeFromString<HomePageResourceShow>("""{"code":200,"data":{"cursor":10,"hasMore":true,"blocks":[{"positionCode":"banner","dslData":null,"nativeData":{"banners":[]}}]}}""")
        assertNull(response.data.blocks.first().dslData)
        assertNotNull(response.data.blocks.first().nativeData)
    }

    @Test fun localCapturesFormThreePageFeed() {
        val dir = System.getenv("HOME_FEED_CAPTURE_DIR")
        assumeTrue(dir != null)
        val pages = (1..3).map { json.decodeFromString<HomePageResourceShow>(File(dir!!, "resp$it.json").readText()).data }
        val feed = pages.reduce { accumulated, page -> accumulated.append(page) }
        assertEquals(listOf(10, 17, 21), pages.map { it.cursor })
        assertEquals(16, feed.blocks.size)
        assertFalse(feed.hasMore)
        assertEquals(pages.flatMap { it.blocks }.map { it.positionCode }, feed.blocks.map { it.positionCode })
    }
}
