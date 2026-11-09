package com.ljyh.mei.utils.image

import org.junit.Assert.*
import org.junit.Test

class LocalAudioArtworkFetcherTest {
    @Test fun onlyMediaStoreAudioItemsUseThumbnailFetcher() {
        assertTrue(isLocalAudioArtworkUri("content", "media", "/external/audio/media/123"))
        assertTrue(isLocalAudioArtworkUri("content", "media", "/external_primary/audio/media/42"))
        assertFalse(isLocalAudioArtworkUri("content", "media", "/external/images/media/123"))
        assertFalse(isLocalAudioArtworkUri("content", "other", "/external/audio/media/123"))
        assertFalse(isLocalAudioArtworkUri("https", "media", "/external/audio/media/123"))
    }

    @Test fun existingCoverTakesPriorityOverLocalFallback() {
        val audio = "content://media/external/audio/media/123"
        assertEquals(audio, localAudioArtworkUri(null, audio))
        assertEquals(audio, localAudioArtworkUri("", audio))
        assertEquals("https://example.com/cover.jpg", localAudioArtworkUri("https://example.com/cover.jpg", audio))
    }
}
