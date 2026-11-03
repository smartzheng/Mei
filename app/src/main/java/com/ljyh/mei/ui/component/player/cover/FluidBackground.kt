package com.ljyh.mei.ui.component.player.cover

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ljyh.mei.constants.MeshFlowSpeedKey
import com.ljyh.mei.constants.MeshPlayingKey
import com.ljyh.mei.constants.MeshRenderScaleKey
import com.ljyh.mei.constants.MeshStaticModeKey
import com.ljyh.mei.constants.MeshSubdivisionKey
import com.ljyh.mei.ui.component.player.cover.mesh.MeshBackgroundView
import com.ljyh.mei.ui.component.player.cover.mesh.MeshBackgroundControl
import com.ljyh.mei.ui.component.player.cover.mesh.MeshTextureView
import android.view.View
import com.ljyh.mei.utils.image.ArtworkSize
import com.ljyh.mei.utils.preferences.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private data class LoadedFluidArtwork(val url: String, val bitmap: Bitmap)

@Composable
fun FluidBackground(
    imageUrl: String?,
    isPlaying: Boolean = true,
    renderAlpha: Float = 1f,
    // Reports the artwork actually drawn by this View; null means the View was released.
    onRenderedArtworkChange: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    clipToContainer: Boolean = false,
) {
    val context = LocalContext.current
    val artworkListener by rememberUpdatedState(onRenderedArtworkChange)
    val (flowSpeed) = rememberPreference(MeshFlowSpeedKey, defaultValue = 0.25f)
    val (renderScale) = rememberPreference(MeshRenderScaleKey, defaultValue = 0.75f)
    val (staticMode) = rememberPreference(MeshStaticModeKey, defaultValue = false)
    val (meshPlaying) = rememberPreference(MeshPlayingKey, defaultValue = true)
    val (subdivision) = rememberPreference(MeshSubdivisionKey, defaultValue = 50)

    // 1. 将图片加载逻辑独立出来，只负责把 Bitmap 提取出来
    // 使用 produceState 是处理这种“异步数据转同步状态”的最佳实践
    val albumArtwork by produceState<LoadedFluidArtwork?>(null, imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            value = null
            return@produceState
        }
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(ArtworkSize.Small.pixels)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                // produceState retains the last value while the next URL loads. Keep that
                // bitmap paired with its own URL so it cannot be submitted as the next album.
                value = LoadedFluidArtwork(imageUrl, result.image.toBitmap())
            }
        }
    }

    // 2. 组装当前需要传递给 View 的所有状态
    val shouldAnimate = !meshPlaying || isPlaying

    key(clipToContainer) {
        var hasRenderedFrame by remember { mutableStateOf(false) }
        AndroidView(
            factory = { ctx ->
                val view: View = if (clipToContainer) MeshTextureView(ctx) else MeshBackgroundView(ctx)
                (view as MeshBackgroundControl).apply {
                    onAlbumRendered = {
                        hasRenderedFrame = true
                        artworkListener?.invoke(it)
                    }
                    setFlowSpeed(flowSpeed)
                    setRenderScale(renderScale)
                    setSubdivision(subdivision)
                    setStaticMode(staticMode)
                    setPlaying(shouldAnimate)
                }
                if (view is MeshBackgroundView) view.setPreserveEGLContextOnPause(true)
                view.alpha = 0f
                view
            },
            onRelease = { view ->
                (view as MeshBackgroundControl).apply {
                    onAlbumRendered = null
                    dispose()
                }
                hasRenderedFrame = false
                artworkListener?.invoke(null)
            },
            update = { view ->
                view.alpha = if (hasRenderedFrame) renderAlpha.coerceIn(0f, 1f) else 0f
                (view as MeshBackgroundControl).apply {
                    albumArtwork?.let { artwork -> setAlbum(artwork.bitmap, artwork.url) }
                    setFlowSpeed(flowSpeed)
                    setRenderScale(renderScale)
                    setSubdivision(subdivision)
                    setStaticMode(staticMode)
                    setPlaying(shouldAnimate)
                }
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}
