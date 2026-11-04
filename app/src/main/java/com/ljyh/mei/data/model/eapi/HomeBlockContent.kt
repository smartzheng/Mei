package com.ljyh.mei.data.model.eapi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Server template names vary. Select by payload structure rather than template-name length. */
fun homeBlockContent(data: JsonObject): JsonObject? {
    fun find(node: JsonObject, depth: Int): JsonObject? {
        if (node["resources"] is JsonArray || node["items"] is JsonArray || node["content"] is JsonObject) return node
        if (depth == 0) return null
        (node["blockResource"] as? JsonObject)?.let { find(it, depth - 1)?.let { found -> return found } }
        return node.entries.asSequence()
            .filter { it.key !in setOf("header", "extInfo", "track", "log") }
            .mapNotNull { (_, value) -> (value as? JsonObject)?.let { find(it, depth - 1) } }
            .firstOrNull()
    }
    return find(data, 4)
}
