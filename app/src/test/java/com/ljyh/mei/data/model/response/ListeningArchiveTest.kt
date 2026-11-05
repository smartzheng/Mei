package com.ljyh.mei.data.model.response

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ListeningArchiveTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun rankKeepsServerOrderAndHandlesBothSongShapes() {
        val response = json.decodeFromString<ListeningRankResponse>("""{
            "code":200,"weekData":[
                {"playCount":7,"song":{"id":2,"name":"Second","ar":[{"id":4,"name":"Singer"}],"al":{"id":6,"name":"Album","picUrl":"cover"},"dt":123}},
                {"score":99,"song":{"id":1,"name":"First","artists":[{"id":5,"name":"Another"}],"album":{"id":7,"name":"Release"},"duration":456}},
                {"song":{"id":0,"name":"Invalid"}},{"song":{"id":2,"name":"Duplicate"}},{}
            ],"allData":[]
        }""")
        val tracks = response.tracks(false)
        assertEquals(listOf(2L, 1L), tracks.map { it.song.id })
        assertEquals(7, tracks[0].playCount)
        assertNull(tracks[1].playCount) // score is not an observed playback count.
        assertEquals(456L, tracks[1].song.duration)
        assertEquals("Another", tracks[1].song.artists.single().name)
        assertTrue(response.tracks(true).isEmpty())
    }

    @Test fun unavailableRankingIsNotMisrepresentedAsAnEmptyHistory() {
        assertThrows(IllegalStateException::class.java) { ListeningRankResponse(403).tracks(false) }
        assertThrows(IllegalStateException::class.java) { ListeningRankResponse(200).tracks(false) }
        assertThrows(IllegalStateException::class.java) { RecentListeningResponse(200).tracks() }
    }

    @Test fun recentRecordsNormalizeSecondsKeepLatestAndSortBeforeDeduplicating() {
        val result = RecentListeningResponse(200, RecentListeningData(listOf(
            RecentListeningDto(ArchiveSongDto(1, "Earlier"), 1_700_000_000),
            RecentListeningDto(ArchiveSongDto(2, "Middle"), 1_700_001_000_000),
            RecentListeningDto(ArchiveSongDto(1, "Latest"), 1_700_002_000),
            RecentListeningDto(ArchiveSongDto(3, "Unknown time"), -1),
            RecentListeningDto(ArchiveSongDto(0, "Invalid"), 1_700_002_000),
        ))).tracks()
        assertEquals(listOf(1L, 2L, 3L), result.map { it.song.id })
        assertEquals("Latest", result.first().song.title)
        assertEquals(1_700_002_000_000, result.first().playedAt)
        assertNull(result.last().playedAt)
    }
}
