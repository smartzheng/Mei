package com.ljyh.mei.data.model.eapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The home feed is polymorphic. Keep only its block discriminator and raw card payload. */
@Serializable
data class HomePageResourceShow(
    val code: Int = 0,
    val data: Data = Data(),
) {
    @Serializable
    data class Data(
        val blocks: List<Block> = emptyList(),
        val cursor: Int = 0,
        val hasMore: Boolean = false,
        val blockCodeOrderList: String? = null,
        val algDemoteBlockCodeOrderList: String? = null,
        val callbackParameters: String? = null,
        val loadedPositionCodes: List<String> = emptyList(),
    ) {
        fun append(page: Data): Data = page.copy(
            blocks = (blocks + page.blocks).distinctBy { it.positionCode },
            loadedPositionCodes = (loadedPositionCodes + blocks.map { it.positionCode } + page.blocks.map { it.positionCode }).distinct(),
            hasMore = page.hasMore && page.cursor > cursor,
            algDemoteBlockCodeOrderList = page.algDemoteBlockCodeOrderList ?: algDemoteBlockCodeOrderList,
        )
        @Serializable
        data class Block(
            val positionCode: String = "",
            val dslData: JsonObject? = null,
            val nativeData: JsonObject? = null,
        ) {
            /** Card payloads are decoded separately because each position code has its own shape. */
            object DslData {
                object BlockResource {
                    @Serializable
                    data class Resource(
                        val resourceId: String = "",
                        val resourceType: String = "",
                        val coverImg: String = "",
                        val title: String = "",
                        val singleLineTitle: String = "",
                        val subTitle: String? = null,
                        val iconDesc: IconDesc? = null,
                        val extInfo: ExtInfo = ExtInfo(),
                        val resourceExtInfo: ResourceExtInfo? = null,
                        val resourceInteractInfo: ResourceInteractInfo? = null,
                    ) {
                        @Serializable
                        data class IconDesc(val image: String = "")

                        @Serializable
                        data class ExtInfo(val songId: JsonElement? = null) {
                            val songIdText: String? get() = (songId as? JsonPrimitive)?.content
                        }

                        @Serializable
                        data class ResourceExtInfo(val coverText: List<String>? = null)

                        @Serializable
                        data class ResourceInteractInfo(val playCount: String? = null)
                    }
                }

                object HomeCommon {
                    object Content {
                        @Serializable
                        data class Item(val items: List<Item> = emptyList()) {
                            @Serializable
                            data class Item(
                                val resourceId: String = "",
                                val coverUrl: String = "",
                                val title: String = "",
                                val artistName: String = "",
                            )
                        }
                    }
                }
            }
        }
    }
}
