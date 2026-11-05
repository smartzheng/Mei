package com.ljyh.mei.utils.image

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.imageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import com.ljyh.mei.ui.component.player.cover.PlayerArtworkImage
import com.ljyh.mei.ui.tablet.TabletContentTestActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real Coil decoding and cache reuse, without network requests or screenshot assertions. */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerArtworkRequestTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun originalArtworkDoesNotKeepTheMiniThumbnailAfterExpandingOrChangingSongs() =
        verifyArtworkHandoff(original = true, expectedPixels = 2048)

    @Test
    fun standardArtworkLoadsAtLeast1600PixelsWhileThePlayerIsStillCollapsed() =
        verifyArtworkHandoff(original = false, expectedPixels = 1600)

    private fun verifyArtworkHandoff(original: Boolean, expectedPixels: Int) {
        val context = RuntimeEnvironment.getApplication()
        val previousLoader = context.imageLoader
        val starts = AtomicInteger()
        val failure = AtomicReference<Throwable>()
        val loader = ImageLoader.Builder(context)
            .allowHardware(false)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
            .diskCache(null)
            .components {
                add(ArtworkRequestInterceptor())
                add(Interceptor { chain ->
                    starts.incrementAndGet()
                    chain.proceed().also { if (it is ErrorResult) failure.set(it.throwable) }
                })
            }
            .build()
        val covers = listOf(createCover(), createCover())
        val urls = covers.map { it.toURI().toString() }
        val current = mutableStateOf(urls.first())
        val coverSize = mutableStateOf(48.dp)
        val loaded = AtomicReference<Pair<String, Float>>()
        SingletonImageLoader.setUnsafe(loader)
        try {
            // Match the service's small preload in the same cache the player consumes.
            for (url in urls) {
                val result = runBlocking {
                    loader.execute(ImageRequest.Builder(context).data(url)
                        .size(ArtworkSize.Small.pixels).precision(Precision.EXACT).build())
                }
                assertTrue(result is SuccessResult)
                assertEquals(192, (result as SuccessResult).image.width)
            }
            TabletContentTestActivity.content = {
                MaterialTheme {
                    val url = current.value
                    Box(Modifier.size(coverSize.value)) {
                        PlayerArtworkImage(url, rememberPlayerArtworkRequest(url, original),
                            Modifier.size(coverSize.value),
                            onPainterReady = { loaded.set(url to it.intrinsicSize.width) })
                    }
                }
            }
            val activity = Robolectric.buildActivity(TabletContentTestActivity::class.java).setup()
            try {
                for (url in urls) {
                    compose.runOnIdle { current.value = url }
                    compose.waitUntil(10_000) {
                        // Coil decodes off the main thread; drain its posted UI result as well.
                        compose.waitForIdle()
                        loaded.get()?.first == url || failure.get() != null
                    }
                    failure.get()?.let { throw AssertionError("Artwork decoding failed", it) }
                    assertTrue("Full artwork must replace the 192px thumbnail while collapsed",
                        loaded.get().second >= expectedPixels)
                    val beforeExpansion = starts.get()
                    compose.runOnIdle { coverSize.value = 320.dp }
                    compose.waitForIdle()
                    assertEquals("Expansion must reuse the loaded artwork", beforeExpansion, starts.get())
                    compose.runOnIdle { coverSize.value = 48.dp }
                    compose.waitForIdle()
                    assertEquals("Collapsing must not restart the artwork request", beforeExpansion, starts.get())
                }
            } finally {
                activity.pause().stop().destroy()
                TabletContentTestActivity.content = {}
            }
        } finally {
            SingletonImageLoader.setUnsafe(previousLoader)
            loader.shutdown()
            covers.forEach { it.delete() }
        }
    }

    private fun createCover(): File {
        val file = File.createTempFile("player-artwork-", ".png")
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
