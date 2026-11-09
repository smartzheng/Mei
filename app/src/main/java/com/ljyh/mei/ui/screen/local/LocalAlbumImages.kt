package com.ljyh.mei.ui.screen.local

import com.ljyh.mei.data.model.domain.SearchAlbum

internal data class LocalAlbumArtworkKey(val title: String, val artists: List<String>)

/** Resolve a local album only when its title and at least one known artist match exactly. */
internal fun exactAlbumImage(
    title: String,
    artists: List<String>,
    albums: List<SearchAlbum>,
): String? {
    val expectedTitle = title.trim()
    if (expectedTitle.isEmpty()) return null
    val expectedArtists = artists.map(String::trim).filter(String::isNotEmpty)
    val matches = albums.filter { album ->
        album.title.trim().equals(expectedTitle, ignoreCase = true) &&
            (expectedArtists.isEmpty() || album.artists.any { artist ->
                artist.name in expectedArtists || artist.name.equalsAny(expectedArtists) ||
                    artist.aliases.any { it.equalsAny(expectedArtists) }
            })
    }
    return matches.asSequence()
        .map { it.coverUrl }
        .filter { it.startsWith("https://") || it.startsWith("http://") }
        .distinct()
        .singleOrNull()
}

private fun String.equalsAny(values: List<String>) = values.any { equals(it, ignoreCase = true) }
