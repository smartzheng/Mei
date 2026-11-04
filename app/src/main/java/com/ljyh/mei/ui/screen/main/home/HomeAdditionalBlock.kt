package com.ljyh.mei.ui.screen.main.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ljyh.mei.data.model.eapi.HomePageResourceShow
import com.ljyh.mei.ui.component.card.PlaylistCard
import com.ljyh.mei.ui.component.layout.meiPageGutter
import com.ljyh.mei.ui.screen.Screen
import kotlinx.serialization.json.*

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

@Composable
internal fun HomeAdditionalBlock(block: HomePageResourceShow.Data.Block, nav: NavController, cardSize: Dp) {
    val context = LocalContext.current
    fun open(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme in listOf("http", "https")) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }
    val data = block.dslData
    if (block.nativeData?.get("banners") is JsonArray) {
        val banners = (block.nativeData?.get("banners") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        LazyRow(contentPadding = PaddingValues(horizontal = meiPageGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(banners) { banner ->
                AsyncImage(model = banner.text("pic"), contentDescription = banner.text("typeTitle"), contentScale = ContentScale.Crop,
                    modifier = Modifier.width(cardSize * 2).height(cardSize).clip(RoundedCornerShape(12.dp)).clickable {
                        val id = banner.text("targetId")
                        when (banner.text("targetType")) {
                            "10" -> Screen.Album.navigate(nav) { addPath(id) }
                            "1000" -> Screen.PlayList.navigate(nav) { addPath(id) }
                            "1" -> open("https://music.163.com/#/song?id=$id")
                            else -> open(banner.text("url"))
                        }
                    })
            }
        }
        return
    }
    if (data == null) return
    val content = if (block.positionCode == "PAGE_RECOMMEND_ARTIST_TREND") {
        data.values.filterIsInstance<JsonObject>().firstOrNull { it["items"] is JsonArray } ?: data
    } else selectSpecialField(data) ?: data
    val title = (data["header"] as? JsonObject)?.text("title")?.takeIf { it.isNotBlank() }
        ?: content.text("title").takeIf { it.isNotBlank() }
        ?: when (block.positionCode) {
            "PAGE_RECOMMEND_SHORTCUT" -> "最近访问"
            "PAGE_RECOMMEND_ARTIST_TREND" -> "歌手动态"
            "PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM" -> "播客推荐"
            else -> (content["header"] as? JsonObject)?.text("title").orEmpty()
        }
    val cards = remember(block) {
        if (block.positionCode == "PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM") {
            (data["items"] as? JsonArray).orEmpty().flatMap {
                ((it as? JsonObject)?.get("items") as? JsonArray).orEmpty()
            }.filterIsInstance<JsonObject>()
        } else {
            ((content["resources"] ?: content["items"]) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        }
    }
    if (cards.isEmpty()) return
    if (title.isNotBlank()) Title(title)
    LazyRow(contentPadding = PaddingValues(horizontal = meiPageGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards) { card ->
            val type = card.text("resourceType")
            val id = card.text("resourceId")
            PlaylistCard(id = id, title = card.text("title"),
                coverImg = card.text("coverImg").ifBlank { card.text("coverUrl") }, cardSize = cardSize, showPlay = false, onPlayClick = {},
                onClick = {
                    when (type) {
                        "album", "digital_album" -> Screen.Album.navigate(nav) { addPath(id) }
                        "list", "playList", "playlist" -> Screen.PlayList.navigate(nav) { addPath(id) }
                        "artist" -> Screen.Artist.navigate(nav) { addPath(id) }
                        "song" -> open("https://music.163.com/#/song?id=$id")
                        "djprogram" -> open("https://music.163.com/#/program?id=$id")
                    }
                })
        }
    }
}
