package com.ljyh.mei.ui.screen.local

import com.ljyh.mei.data.model.domain.SearchArtist
import org.junit.Assert.*
import org.junit.Test

class LocalArtistImagesTest {
    @Test fun selectsOnlyUnambiguousExactNames() {
        val match = SearchArtist(1, "周杰伦", "https://example.com/artist.jpg", emptyList())
        assertEquals(match.imageUrl, exactArtistImage("周杰伦", listOf(match)))
        assertNull(exactArtistImage("周杰", listOf(match)))
        assertNull(exactArtistImage("周杰伦", listOf(match, match.copy(id = 2))))
        assertNull(exactArtistImage("周杰伦", listOf(match.copy(imageUrl = null))))
        assertNull(exactArtistImage("周杰伦", listOf(match.copy(imageUrl = "file:///private"))))
    }
}
