package com.ljyh.mei.data.repository

import com.ljyh.mei.AppContext
import com.ljyh.mei.constants.UserIdKey
import com.ljyh.mei.data.model.eapi.HomePageResourceShow
import com.ljyh.mei.data.model.weapi.buildGetHomePageResourceShow
import com.ljyh.mei.data.model.weapi.buildAdExtJson
import com.ljyh.mei.data.network.api.ApiService
import com.ljyh.mei.data.network.api.EApiService
import com.ljyh.mei.data.network.safeApiCall
import com.ljyh.mei.utils.preferences.dataStore
import com.ljyh.mei.utils.preferences.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.io.File

class HomeRepository(private val eApiService: EApiService, private val apiService: ApiService) {
    private val context = AppContext.instance
    private val adDeviceIdProvider = AdDeviceIdProvider(context, eApiService)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    suspend fun getHomePageResourceShow(refresh: Boolean = false) = fetch(null, refresh)
    suspend fun loadMore(previous: HomePageResourceShow.Data) = fetch(previous, false)

    private suspend fun fetch(previous: HomePageResourceShow.Data?, refresh: Boolean) = withContext(Dispatchers.IO) {
        safeApiCall {
            val user = context.dataStore[UserIdKey].orEmpty()
            // New format includes pagination state; old block-only cache cannot resume a feed.
            val cache = File(context.filesDir, "home_feed_v2_${user.hashCode()}.json")
            if (previous == null && !refresh && cache.exists() &&
                System.currentTimeMillis() - cache.lastModified() in 0 until 30 * 60 * 1000L) {
                runCatching { json.decodeFromString<HomePageResourceShow.Data>(cache.readText()) }
                    .getOrNull()?.let { return@safeApiCall it }
            }
            val adId = adDeviceIdProvider.get()
            val request = buildGetHomePageResourceShow("true") {
                isFirstScreen = (previous == null).toString()
                cursor = (previous?.cursor ?: 0).toString()
                loadedPositionCodes = previous?.let {
                    json.encodeToString((it.loadedPositionCodes + it.blocks.map { block -> block.positionCode }).distinct())
                }.orEmpty()
                blockCodeOrderList = previous?.blockCodeOrderList
                if (previous != null) algDemoteBlockCodeOrderList = null
                callbackParameters = previous?.callbackParameters.orEmpty()
                clientCacheBlockCode = "[]"
                val metrics = context.resources.displayMetrics
                widthDp = (metrics.widthPixels / metrics.density).toString()
                heightDp = (metrics.heightPixels / metrics.density).toString()
                val extras = json.parseToJsonElement(extJson).jsonObject.toMutableMap()
                extras["refreshAction"] = JsonPrimitive(if (previous != null) "loadMore" else if (refresh) "pull" else "init")
                extras["firstRequestPerLaunch"] = JsonPrimitive(previous == null && !refresh)
                extJson = JsonObject(extras).toString()
                adExtJson = json.encodeToString(buildAdExtJson { adDeviceId = adId })
            }
            val response = eApiService.getHomePageResourceShow(request)
            check(response.code == 200) { "Home request failed: ${response.code}" }
            val page = response.data
            val combined = previous?.append(page) ?: page.copy(loadedPositionCodes = page.blocks.map { it.positionCode }.distinct())
            if (context.dataStore[UserIdKey].orEmpty() == user) {
                // Cache failure should not turn a successful network response into an error.
                runCatching { cache.writeText(json.encodeToString(combined)) }
            }
            combined
        }
    }
}
