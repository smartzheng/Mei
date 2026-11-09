package com.ljyh.mei.utils.image

import android.net.Uri as AndroidUri
import android.util.Size as AndroidSize
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun isLocalAudioArtworkUri(scheme: String?, authority: String?, path: String?): Boolean =
    scheme == "content" && authority == "media" &&
        path.orEmpty().matches(Regex("/[^/]+/audio/media/[0-9]+"))

internal fun localAudioArtworkUri(existingCover: String?, audioUri: String): String =
    existingCover?.takeIf { it.isNotBlank() } ?: audioUri

/** Read embedded or system album art through the shared image loader. */
internal class LocalAudioArtworkFetcher(private val uri: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        val width = (options.size.width as? Dimension.Pixels)?.px?.coerceIn(1, 1600) ?: 512
        val height = (options.size.height as? Dimension.Pixels)?.px?.coerceIn(1, 1600) ?: 512
        val bitmap = options.context.contentResolver.loadThumbnail(
            AndroidUri.parse(uri.toString()), AndroidSize(width, height), null,
        )
        ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isLocalAudioArtworkUri(data.scheme, data.authority, data.path)) return null
            return LocalAudioArtworkFetcher(data, options)
        }
    }
}
