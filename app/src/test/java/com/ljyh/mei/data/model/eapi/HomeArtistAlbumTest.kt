package com.ljyh.mei.data.model.eapi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HomeArtistAlbumTest {
    @Test fun albumCardsUseAlbumRouteWithoutSongLookup() = runBlocking {
        for (type in listOf("album", "digital_album")) {
            assertEquals("101", resolveHomeArtistAlbum(type, "101") { error("Song lookup must not run") })
        }
    }

    @Test fun songCardsResolveTheirContainingAlbum() = runBlocking {
        val albumId = resolveHomeArtistAlbum("song", "123") { songId ->
            assertEquals("123", songId)
            456L
        }
        assertEquals("456", albumId)
    }

    @Test fun unresolvedSongNeverUsesSongIdAsAlbumId() = runBlocking {
        assertNull(resolveHomeArtistAlbum("song", "123") { null })
        assertNull(resolveHomeArtistAlbum("song", "123") { 0L })
        assertNull(resolveHomeArtistAlbum("song", "invalid") { error("Must reject invalid ID") })
    }
}
