package com.ljyh.mei.data.network

import com.ljyh.mei.data.model.response.PlaylistNameResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HomePlaylistNameTest {
    @Test fun headerLookupDoesNotRequestTracksAndAcceptsHeaderOnlyResponse() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v6/playlist/detail", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("0", body.getValue("n").jsonPrimitive.content)
            assertEquals("0", body.getValue("s").jsonPrimitive.content)
            respond("""{"code":200,"playlist":{"id":123,"name":"十月回忆"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val response: PlaylistNameResponse = NeteaseHttp(client, Json { ignoreUnknownKeys = true }).post(
                "https://interface.music.163.com", "/api/v6/playlist/detail",
                com.ljyh.mei.data.model.api.GetPlaylistDetail("123", n = "0", s = "0"),
            )
            assertEquals("十月回忆", response.playlist?.name)
        } finally { client.close() }
    }
}
