package com.ljyh.mei.data.network

import com.ljyh.mei.data.network.api.EApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AdDeviceIdApiTest {
    @Test
    fun postsParametersAndParsesResponse() = runBlocking {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(MockEngine { request ->
            assertEquals("/eapi/ad/device/deviceid", request.url.encodedPath)
            val body = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("adDeviceId", "oaid", "androidId", "imei"), body.keys)
            assertEquals("synthetic", body.getValue("androidId").jsonPrimitive.content)
            respond("""{"code":200,"data":{"adDeviceId":"test-id"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = EApiService(NeteaseHttp(client, json))
            val response = api.getAdDeviceId(mapOf("adDeviceId" to "", "oaid" to "", "androidId" to "synthetic", "imei" to "null"))
            assertEquals(200, response.code)
            assertEquals("test-id", response.data?.adDeviceId)
        } finally {
            client.close()
        }
    }
}
