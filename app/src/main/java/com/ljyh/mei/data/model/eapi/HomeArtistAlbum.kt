package com.ljyh.mei.data.model.eapi

/** A song identifier must be resolved before entering the album route. */
suspend fun resolveHomeArtistAlbum(type: String, id: String, songAlbum: suspend (String) -> Long?): String? {
    if ((id.toLongOrNull() ?: 0) <= 0) return null
    return when (type) {
        "album", "digital_album" -> id
        "song" -> songAlbum(id)?.takeIf { it > 0 }?.toString()
        else -> null
    }
}
