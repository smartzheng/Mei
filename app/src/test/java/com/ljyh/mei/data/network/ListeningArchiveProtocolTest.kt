package com.ljyh.mei.data.network

import com.ljyh.mei.data.network.api.WeApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ListeningArchiveProtocolTest {
    @Test fun rankingAndCloudHistoryUseTheirOwnWeapiPathsAndFields() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("music.163.com", request.url.host)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/weapi/v1/play/record" -> {
                    assertEquals("123", body.getValue("uid").jsonPrimitive.content)
                    assertEquals(if (paths.size == 1) "1" else "0", body.getValue("type").jsonPrimitive.content)
                    respond("""{"code":200,"weekData":[],"allData":[]}""")
                }
                "/weapi/play-record/song/list" -> {
                    assertEquals("100", body.getValue("limit").jsonPrimitive.content)
                    respond("""{"code":200,"data":{"list":[]}}""")
                }
                else -> error("Unexpected archive endpoint")
            }
        })
        try {
            val service = WeApiService(NeteaseHttp(client, Json { ignoreUnknownKeys = true }))
            assertTrue(service.getListeningRank("123", false).tracks(false).isEmpty())
            assertTrue(service.getListeningRank("123", true).tracks(true).isEmpty())
            assertTrue(service.getRecentListening().tracks().isEmpty())
            assertEquals(3, paths.size)
        } finally { client.close() }
    }
}
