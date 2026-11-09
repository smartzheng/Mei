package com.ljyh.mei.ui.screen.local

import com.ljyh.mei.data.model.domain.SearchArtist

/** Avoid guessing when the provider returns multiple people with the same name. */
internal fun exactArtistImage(name: String, artists: List<SearchArtist>): String? {
    val matches = artists.filter { it.name.trim().equals(name.trim(), ignoreCase = true) }
        .distinctBy { it.id }
    return matches.singleOrNull()?.imageUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}
