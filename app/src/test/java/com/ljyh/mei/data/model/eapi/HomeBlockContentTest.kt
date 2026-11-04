package com.ljyh.mei.data.model.eapi

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class HomeBlockContentTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun selectsPayloadInsteadOfLongTitleTemplateName() {
        val data = json.parseToJsonElement("""{"very_long_template_with_only_title":{"title":"Title"},"cards":{"items":[{"resourceId":"1"}]}}""").jsonObject
        assertEquals(1, homeBlockContent(data)?.get("items")?.jsonArray?.size)
    }

    @Test fun acceptsNestedAnnualPlaylistItems() {
        val data = json.parseToJsonElement("""{"template":{"blockResource":{"header":{"title":"歌单"},"items":[{"resourceId":"1","title":"Mix","coverImg":"cover"}]}}}""").jsonObject
        val content = requireNotNull(homeBlockContent(data))
        assertEquals("歌单", content["header"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(1, content["items"]!!.jsonArray.size)
    }

    @Test fun capturedModulesContainRenderableCards() {
        val dir = System.getenv("HOME_FEED_CAPTURE_DIR")
        assumeTrue(dir != null)
        val blocks = (1..3).flatMap {
            json.decodeFromString<HomePageResourceShow>(File(dir!!, "resp$it.json").readText()).data.blocks
        }
        for (block in blocks) {
            if (block.positionCode.contains("BANNER")) {
                assertTrue((block.nativeData?.get("banners") as? JsonArray).orEmpty().isNotEmpty())
                continue
            }
            val content = requireNotNull(block.dslData?.let(::homeBlockContent)) { block.positionCode }
            val array = (content["resources"] ?: content["items"] ?: (content["content"] as? JsonObject)?.get("items")) as? JsonArray
            assertTrue(block.positionCode, !array.isNullOrEmpty())
            if (content["resources"] is JsonArray || block.positionCode.contains("MONTH_YEAR")) {
                for (item in requireNotNull(array)) {
                    json.decodeFromJsonElement<HomePageResourceShow.Data.Block.DslData.BlockResource.Resource>(item)
                }
            }
        }
        assertEquals(16, blocks.size)
    }
    @Test fun reportedMissingSectionsHaveTitlesAndCards() {
        val dir = System.getenv("HOME_FEED_CAPTURE_DIR")
        assumeTrue(dir != null)
        val blocks = (1..3).flatMap {
            json.decodeFromString<HomePageResourceShow>(File(dir!!, "resp$it.json").readText()).data.blocks
        }.associateBy { it.positionCode }
        val expected = mapOf(
            "PAGE_RECOMMEND_FEELING_PLAYLIST_LOCATION" to ("心情氛围" to 6),
            "PAGE_RECOMMEND_SHORTCUT" to ("最近常听" to 11),
            "PAGE_RECOMMEND_COMBINATION" to ("你的专属推荐歌单" to 6),
            "PAGE_RECOMMEND_SCENE_PLAYLIST_LOCATION" to ("场景歌单" to 6),
            "PAGE_RECOMMEND_FIRM_PLAYLIST" to ("影视原声" to 6),
            "PAGE_RECOMMEND_MONTH_YEAR_PLAYLIST" to ("回忆歌单" to 6),
        )
        expected.forEach { (code, section) ->
            val content = requireNotNull(homeBlockContent(requireNotNull(blocks.getValue(code).dslData)))
            val title = (content["title"] as? JsonPrimitive)?.content
                ?: (content["header"] as? JsonObject)?.get("title")?.jsonPrimitive?.content
            assertEquals(code, section.first, title)
            val cards = (content["resources"] ?: content["items"]) as JsonArray
            assertEquals(code, section.second, cards.size)
            cards.forEach { raw ->
                val card = json.decodeFromJsonElement<HomePageResourceShow.Data.Block.DslData.BlockResource.Resource>(raw)
                assertTrue(code, card.resourceId.isNotBlank())
                assertTrue(code, card.title.isNotBlank())
                assertTrue(code, card.coverImg.isNotBlank())
            }
        }
    }

}
