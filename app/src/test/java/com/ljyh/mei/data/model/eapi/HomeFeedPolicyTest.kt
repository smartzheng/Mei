package com.ljyh.mei.data.model.eapi

import org.junit.Assert.*
import org.junit.Test

class HomeFeedPolicyTest {
    private fun block(code: String) = HomePageResourceShow.Data.Block(positionCode = code)

    @Test fun radarIsSecondEvenWhenServerReturnsItLater() {
        val blocks = listOf("PAGE_RECOMMEND_DAILY_RECOMMEND", "PAGE_RECOMMEND_SHORTCUT",
            "PAGE_RECOMMEND_COMBINATION", "PAGE_RECOMMEND_RADAR").map(::block)
        assertEquals("PAGE_RECOMMEND_RADAR", HomeFeedPolicy.visibleBlocks(blocks)[1].positionCode)
    }

    @Test fun musicHomeDoesNotRenderPodcastOrAudiobookTemplates() {
        val blocks = listOf("PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM", "PAGE_RECOMMEND_AUDIOBOOK",
            "PAGE_RECOMMEND_PODCAST_MUSIC_RADIO", "PAGE_RECOMMEND_RADAR").map(::block)
        assertEquals(listOf("PAGE_RECOMMEND_RADAR"), HomeFeedPolicy.visibleBlocks(blocks).map { it.positionCode })
    }

    @Test fun requestedOrderIncludesAllReportedMissingMusicSections() {
        val expected = setOf("PAGE_RECOMMEND_FEELING_PLAYLIST_LOCATION", "PAGE_RECOMMEND_SHORTCUT",
            "PAGE_RECOMMEND_COMBINATION", "PAGE_RECOMMEND_SCENE_PLAYLIST_LOCATION",
            "PAGE_RECOMMEND_FIRM_PLAYLIST", "PAGE_RECOMMEND_MONTH_YEAR_PLAYLIST")
        assertTrue(HomeFeedPolicy.moduleOrder.containsAll(expected))
        assertEquals(HomeFeedPolicy.moduleOrder.size, HomeFeedPolicy.moduleOrder.distinct().size)
    }

    @Test fun filteringDisplayDoesNotChangeServerPaginationState() {
        val first = HomePageResourceShow.Data(blocks = listOf(block("PAGE_RECOMMEND_RADAR")), cursor = 10, hasMore = true)
        val next = first.append(HomePageResourceShow.Data(blocks = listOf(block("PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM")),
            cursor = 17, hasMore = true, blockCodeOrderList = "remaining"))
        assertEquals(1, HomeFeedPolicy.visibleBlocks(next.blocks).size)
        assertEquals(17, next.cursor)
        assertTrue(next.hasMore)
        assertTrue(next.loadedPositionCodes.contains("PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM"))
        assertEquals("remaining", next.blockCodeOrderList)
    }
    @Test fun unfamiliarMusicModulesAreNotSilentlyDropped() {
        val blocks = listOf("PAGE_RECOMMEND_MY_SHEET", "PAGE_RECOMMEND_RADAR").map(::block)
        assertEquals(listOf("PAGE_RECOMMEND_RADAR", "PAGE_RECOMMEND_MY_SHEET"),
            HomeFeedPolicy.visibleBlocks(blocks).map { it.positionCode })
    }

    @Test fun fallbackOrderMatchesCapturedFirstRequest() {
        val dir = System.getenv("HOME_FEED_CAPTURE_DIR")
        org.junit.Assume.assumeTrue(dir != null)
        val request = kotlinx.serialization.json.Json.parseToJsonElement(java.io.File(dir!!, "req1.json").readText())
        val data = (request as kotlinx.serialization.json.JsonObject).getValue("data") as kotlinx.serialization.json.JsonObject
        val encoded = (data.getValue("algDemoteBlockCodeOrderList") as kotlinx.serialization.json.JsonPrimitive).content
        val captured = kotlinx.serialization.json.Json.decodeFromString<List<String>>(encoded)
        assertEquals(captured, HomeFeedPolicy.fallbackOrder)
    }

}
