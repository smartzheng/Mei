package com.ljyh.mei.data.model.weapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ljyh.mei.AppContext
import com.ljyh.mei.constants.AndroidUserAgent
import com.ljyh.mei.constants.UserIdKey
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.system.Os
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random
import com.ljyh.mei.utils.preferences.dataStore
import com.ljyh.mei.utils.preferences.get
import com.ljyh.mei.utils.netease.getDeviceId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ==========================
 * 顶层请求参数
 * ========================== */
@Serializable
data class GetHomePageResourceShow(
    @SerialName("pageCode") val pageCode: String,
    @SerialName("isFirstScreen") val isFirstScreen: String,
    @SerialName("cursor") val cursor: String,
    @SerialName("refresh") val refresh: String,
    @SerialName("widthDp") val widthDp: String,
    @SerialName("heightDp") val heightDp: String,
    @SerialName("loadedPositionCodes") val loadedPositionCodes: String,
    @SerialName("clientCacheBlockCode") val clientCacheBlockCode: String,
    @SerialName("callbackParameters") val callbackParameters: String,
    @SerialName("extJson") val extJson: String,
    @SerialName("pageStyleType") val pageStyleType: String,
    @SerialName("adExtJson") val adExtJson: String,
    @SerialName("reqTimeStamp") val reqTimeStamp: String,
    @SerialName("clientTime") val clientTime: String,
    @SerialName("ruleJson") val ruleJson: String,
    @SerialName("algDemoteBlockCodeOrderList") val algDemoteBlockCodeOrderList: String?,
    @SerialName("blockCodeOrderList") val blockCodeOrderList: String? = null,
    @SerialName("header") val header: String,
    @SerialName("e_r") val eR: Boolean
)

/* ==========================
 * AdExtJson 及其嵌套结构
 * ========================== */
@Serializable
data class AdExtJson(
    @SerialName("terminal") val terminal: String,
    @SerialName("network") val network: Int,
    @SerialName("op") val op: Int,
    @SerialName("dev_type") val devType: Int,
    @SerialName("pid") val pid: String,
    @SerialName("resolution") val resolution: Resolution,
    @SerialName("adReqId") val adReqId: String,
    @SerialName("memory") val memory: String,
    @SerialName("disk") val disk: String,
    @SerialName("mobilename") val mobilename: String,
    @SerialName("ipv4") val ipv4: String?,
    @SerialName("ipv6") val ipv6: String?,
    @SerialName("android_id") val androidId: String,
    @SerialName("imei") val imei: String,
    @SerialName("adDeviceId") val adDeviceId: String? = null,
    @SerialName("extDevJson") val extDevJson: String,
    @SerialName("oaid") val oaid: String? = null,
    @SerialName("verCodeOfHms") val verCodeOfHms: Long? = null,
    @SerialName("verCodeOfAG") val verCodeOfAG: Long? = null,
    @SerialName("manufacturer") val manufacturer: String,
    @SerialName("lbs") val lbs: String,
    @SerialName("opensdkVer") val opensdkVer: Int,
    @SerialName("wxApiVer") val wxApiVer: String,
    @SerialName("wxInstalled") val wxInstalled: Boolean,
    @SerialName("supportWechatCanvas") val supportWechatCanvas: Boolean,
    @SerialName("supportQuickApp") val supportQuickApp: Boolean,
    @SerialName("newAgent") val newAgent: String,
    @SerialName("useragent") val useragent: String,
    @SerialName("teenMode") val teenMode: Boolean,
    @SerialName("homePageType") val homePageType: Int,
    @SerialName("sourceFrame") val sourceFrame: String,
    @SerialName("subOsName") val subOsName: String,
    @SerialName("subOsVersion") val subOsVersion: String,
    @SerialName("bootMark") val bootMark: String?,
    @SerialName("updateMark") val updateMark: String?,
    @SerialName("vipRight") val vipRight: Boolean?,
    @SerialName("isNativeSampling") val isNativeSampling: Boolean,
    @SerialName("ext") val ext: Ext
) {
    @Serializable
    data class Resolution(
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int
    )

    @Serializable

    data class Ext(
        @SerialName("bannerRefreshTypes") val bannerRefreshTypes: String,
        @SerialName("adDeviceId") val adDeviceId: String? = null,
        @SerialName("extDevJson") val extDevJson: String,
        @SerialName("oaid") val oaid: String? = null,
        @SerialName("verCodeOfHms") val verCodeOfHms: Long? = null,
        @SerialName("verCodeOfAG") val verCodeOfAG: Long? = null,
        @SerialName("teenMode") val teenMode: Boolean,
        @SerialName("homePageType") val homePageType: Int,
        @SerialName("sourceFrame") val sourceFrame: String,
        @SerialName("opensdkVer") val opensdkVer: Int,
        @SerialName("wxApiVer") val wxApiVer: String,
        @SerialName("wxInstalled") val wxInstalled: Boolean,
        @SerialName("supportWechatCanvas") val supportWechatCanvas: Boolean,
        @SerialName("supportQuickApp") val supportQuickApp: Boolean,
        @SerialName("subOsName") val subOsName: String,
        @SerialName("subOsVersion") val subOsVersion: String,
        @SerialName("memory") val memory: String,
        @SerialName("disk") val disk: String,
        @SerialName("ipv4") val ipv4: String?,
        @SerialName("ipv6") val ipv6: String?,
        @SerialName("bootMark") val bootMark: String?,
        @SerialName("updateMark") val updateMark: String?
    )
}

/* =========================================================
 * 以下仅做“常量池”用，不对外暴露，避免调用方误用默认值
 * ========================================================= */
private object DefaultBuilder {

    const val PAGE_CODE = "HOME_RECOMMEND_PAGE"
    const val IS_FIRST_SCREEN = "true"
    const val CURSOR = "0"
    const val WIDTH_DP = "1080"
    const val HEIGHT_DP = "1920"
    const val LOADED_POSITION_CODES = ""
    var CLIENT_CACHE_BLOCK_CODE: String = Json.encodeToString(
        listOf(
            "PAGE_RECOMMEND_RADAR",
            "PAGE_RECOMMEND_SPECIAL_CLOUD_VILLAGE_PLAYLIST",
            "PAGE_RECOMMEND_RED_SIMILAR_SONG",
            "PAGE_RECOMMEND_DAILY_RECOMMEND"
        )
    )
    const val CALLBACK_PARAMETERS = ""
    const val EXT_JSON =
        """{"fmName":{"fmTitle":"漫游","fmLongTitle":"私人漫游"},"currentExploreHomeType":"main","homeFrameworkType":"fastPlay","latitude":"4.9E-324","homeReqSource":"home","adSceneExt":"","clientLibraAbTest":{"similarFmNameTest0422":"c","fmNameTest0422":"c"},"network":"wifi","carrier":"","forceFreshForNewUser":false,"bluetooth":false,"requestLongVideoBanner":true,"currentNewUserExploreHomeType":"main","longitude":"4.9E-324","refreshAction":"init","firstRequestPerLaunch":true,"clientMobileSize":"{\"width\":720,\"length\":1475}","noteHomeType":"note"}"""
    const val PAGE_STYLE_TYPE = "cutBlock"
    const val RULE_JSON = "{}"
    val ALG_DEMOTE_BLOCK_CODE_ORDER_LIST = Json.encodeToString(
        listOf(
            "PAGE_RECOMMEND_DAILY_RECOMMEND",
            "PAGE_RECOMMEND_VIP_SMALL_CARD",
            "PAGE_RECOMMEND_VIP_MODULE",
            "PAGE_RECOMMEND_BANNER_6",
            "PAGE_RECOMMEND_RADAR",
            "PAGE_RECOMMEND_BANNER_1",
            "PAGE_RECOMMEND_SPECIAL_CLOUD_VILLAGE_PLAYLIST",
            "PAGE_RECOMMEND_SHORTCUT",
            "PAGE_RECOMMEND_RED_SIMILAR_SONG",
            "PAGE_RECOMMEND_PRIVATE_RCMD_SONG",
            "PAGE_RECOMMEND_MIXED_ARTIST_PLAYLIST",
            "PAGE_RECOMMEND_SURVEY",
            "PAGE_RECOMMEND_PODCAST_MUSIC_RADIO",
            "PAGE_RECOMMEND_NEW_SONG_AND_ALBUM",
            "PAGE_RECOMMEND_RANK",
            "PAGE_RECOMMEND_MUSIC_FM_LIST",
            "PAGE_RECOMMEND_PODCAST_RADIO_PROGRAM"
        )
    )
    const val HEADER = "{}"
    const val ER = true
}

/* =========================================================
 * 构造 DSL
 * ========================================================= */
inline fun buildGetHomePageResourceShow(
    refresh: String,
    block: GetHomePageResourceShowDsl.() -> Unit = {}
): GetHomePageResourceShow {
    val dsl = GetHomePageResourceShowDsl(refresh).apply(block)
    return GetHomePageResourceShow(
        pageCode = dsl.pageCode,
        isFirstScreen = dsl.isFirstScreen,
        cursor = dsl.cursor,
        refresh = dsl.refresh,
        widthDp = dsl.widthDp,
        heightDp = dsl.heightDp,
        loadedPositionCodes = dsl.loadedPositionCodes,
        clientCacheBlockCode = dsl.clientCacheBlockCode,
        callbackParameters = dsl.callbackParameters,
        extJson = dsl.extJson,
        pageStyleType = dsl.pageStyleType,
        adExtJson = dsl.adExtJson,
        reqTimeStamp = dsl.reqTimeStamp,
        clientTime = dsl.clientTime,
        ruleJson = dsl.ruleJson,
        algDemoteBlockCodeOrderList = dsl.algDemoteBlockCodeOrderList,
        header = dsl.header,
        blockCodeOrderList = dsl.blockCodeOrderList,
        eR = dsl.eR
    )
}

class GetHomePageResourceShowDsl(
    val refresh: String
) {
    var pageCode: String = DefaultBuilder.PAGE_CODE
    var isFirstScreen: String = DefaultBuilder.IS_FIRST_SCREEN
    var cursor: String = DefaultBuilder.CURSOR
    var widthDp: String = DefaultBuilder.WIDTH_DP
    var heightDp: String = DefaultBuilder.HEIGHT_DP
    var loadedPositionCodes: String = DefaultBuilder.LOADED_POSITION_CODES
    var clientCacheBlockCode: String = DefaultBuilder.CLIENT_CACHE_BLOCK_CODE
    var callbackParameters: String = DefaultBuilder.CALLBACK_PARAMETERS
    var extJson: String = DefaultBuilder.EXT_JSON
    var pageStyleType: String = DefaultBuilder.PAGE_STYLE_TYPE
    var adExtJson: String = Json { explicitNulls = false }.encodeToString(buildAdExtJson { })
    var reqTimeStamp: String = System.currentTimeMillis().toString()
    var clientTime: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    var ruleJson: String = DefaultBuilder.RULE_JSON
    var blockCodeOrderList: String? = null
    var algDemoteBlockCodeOrderList: String? = DefaultBuilder.ALG_DEMOTE_BLOCK_CODE_ORDER_LIST
    var header: String = DefaultBuilder.HEADER
    var eR: Boolean = DefaultBuilder.ER
}

/* ---------------- AdExtJson 构造器 ---------------- */
inline fun buildAdExtJson(block: AdExtJsonDsl.() -> Unit = {}): AdExtJson {
    val dsl = AdExtJsonDsl().apply(block)
    return AdExtJson(
        terminal = dsl.terminal,
        network = dsl.network,
        op = dsl.op,
        devType = dsl.devType,
        pid = dsl.pid,
        resolution = AdExtJson.Resolution(dsl.resolutionWidth, dsl.resolutionHeight),
        adReqId = dsl.adReqId,
        memory = dsl.memory,
        disk = dsl.disk,
        mobilename = dsl.mobilename,
        ipv4 = dsl.ipv4,
        ipv6 = dsl.ipv6,
        androidId = dsl.androidId,
        imei = dsl.imei,
        adDeviceId = dsl.adDeviceId,
        extDevJson = dsl.extDevJson,
        oaid = dsl.oaid,
        verCodeOfHms = dsl.verCodeOfHms,
        verCodeOfAG = dsl.verCodeOfAG,
        manufacturer = dsl.manufacturer,
        lbs = dsl.lbs,
        opensdkVer = dsl.opensdkVer,
        wxApiVer = dsl.wxApiVer,
        wxInstalled = dsl.wxInstalled,
        supportWechatCanvas = dsl.supportWechatCanvas,
        supportQuickApp = dsl.supportQuickApp,
        newAgent = dsl.newAgent,
        useragent = dsl.useragent,
        teenMode = dsl.teenMode,
        homePageType = dsl.homePageType,
        sourceFrame = dsl.sourceFrame,
        subOsName = dsl.subOsName,
        subOsVersion = dsl.subOsVersion,
        bootMark = dsl.bootMark,
        updateMark = dsl.updateMark,
        vipRight = dsl.vipRight,
        isNativeSampling = dsl.isNativeSampling,
        ext = AdExtJson.Ext(
            bannerRefreshTypes = dsl.extBannerRefreshTypes,
            adDeviceId = dsl.adDeviceId,
            extDevJson = dsl.extDevJson,
            oaid = dsl.oaid,
            verCodeOfHms = dsl.verCodeOfHms,
            verCodeOfAG = dsl.verCodeOfAG,
            teenMode = dsl.teenMode,
            homePageType = dsl.homePageType,
            sourceFrame = dsl.sourceFrame,
            opensdkVer = dsl.opensdkVer,
            wxApiVer = dsl.wxApiVer,
            wxInstalled = dsl.wxInstalled,
            supportWechatCanvas = dsl.supportWechatCanvas,
            supportQuickApp = dsl.supportQuickApp,
            subOsName = dsl.subOsName,
            subOsVersion = dsl.subOsVersion,
            memory = dsl.memory,
            disk = dsl.disk,
            ipv4 = dsl.ipv4,
            ipv6 = dsl.ipv6,
            bootMark = dsl.bootMark,
            updateMark = dsl.updateMark
        )
    )
}

class AdExtJsonDsl {
    private val context = AppContext.instance
    private val metrics = context.resources.displayMetrics
    var terminal: String = URLEncoder.encode(Build.MODEL, "UTF-8")
    var network: Int = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        when {
            capabilities == null -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
            else -> 2
        }
    }.getOrDefault(0)
    var op: Int = runCatching {
        when ((context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simOperator) {
            "46001", "46006", "46009" -> 3
            "46003", "46005", "46011" -> 1
            "46000", "46002", "46007", "46008" -> 2
            else -> 0
        }
    }.getOrDefault(0)
    var devType: Int = if (context.resources.configuration.smallestScreenWidthDp >= 600) 2 else 1
    var pid: String = "4002"
    var resolutionWidth: Int = metrics.widthPixels
    var resolutionHeight: Int = metrics.heightPixels
    var adReqId: String = "${context.dataStore[UserIdKey] ?: "0"}_${System.currentTimeMillis()}_${Random.nextInt(9999)}"
    var memory: String = runCatching {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        info.totalMem.toString()
    }.getOrDefault("")
    var disk: String = runCatching { StatFs(Environment.getDataDirectory().path).totalBytes.toString() }.getOrDefault("")
    var mobilename: String = Build.MODEL
    var ipv4: String? = null
    var ipv6: String? = null
    var androidId: String = getDeviceId(context)
    var imei: String = "null"
    var adDeviceId: String? = null
    var oaid: String? = null
    var verCodeOfHms: Long? = null
    var verCodeOfAG: Long? = null
    val extDevJson: String get() = kotlinx.serialization.json.buildJsonObject {
        adDeviceId?.let { put("ad_device_id", kotlinx.serialization.json.JsonPrimitive(it)) }
    }.toString()
    var manufacturer: String = Build.MANUFACTURER
    var lbs: String = "{}"
    var opensdkVer: Int = 638067200
    var wxApiVer: String = ""
    var wxInstalled: Boolean = false
    var supportWechatCanvas: Boolean = true
    var supportQuickApp: Boolean = true
    var newAgent: String = AndroidUserAgent
    var useragent: String = AndroidUserAgent
    var teenMode: Boolean = false
    var homePageType: Int = 1
    var sourceFrame: String = "rcmd"
    var subOsName: String = ""
    var subOsVersion: String = ""
    var bootMark: String? = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim().ifEmpty { null } }.getOrNull()
    var updateMark: String? = runCatching {
        val stat = Os.stat("/data/data")
        "${stat.st_atime}.${stat.st_atim.tv_nsec}"
    }.getOrNull()
    var vipRight: Boolean? = null
    var isNativeSampling: Boolean = false
    var extBannerRefreshTypes: String = "[0]"
}
