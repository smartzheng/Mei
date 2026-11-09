package com.ljyh.mei.data.repository

import org.junit.Assert.*
import org.junit.Test

class LocalFolderFilterTest {
    @Test fun allAllowsEveryFolder() {
        assertTrue(LocalFolderFilter().allows(null))
        assertTrue(LocalFolderFilter().allows("Music/Albums"))
    }

    @Test fun includeMatchesFolderAndDescendantsOnly() {
        val filter = LocalFolderFilter(LocalFolderMode.INCLUDE, setOf("Music/Albums/"))
        assertTrue(filter.allows("music/albums"))
        assertTrue(filter.allows("Music/Albums/Artist/"))
        assertFalse(filter.allows("Music/Albums2"))
        assertFalse(filter.allows("Music"))
        assertFalse(LocalFolderFilter(LocalFolderMode.INCLUDE).allows("Music"))
    }

    @Test fun excludeRejectsFolderAndDescendants() {
        val filter = LocalFolderFilter(LocalFolderMode.EXCLUDE, setOf("Music/Voice"))
        assertFalse(filter.allows("Music/Voice"))
        assertFalse(filter.allows("Music/Voice/Recordings"))
        assertTrue(filter.allows("Music/Voiceovers"))
        assertTrue(filter.allows("Music"))
    }
}
