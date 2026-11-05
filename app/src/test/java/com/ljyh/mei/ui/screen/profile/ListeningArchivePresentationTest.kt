package com.ljyh.mei.ui.screen.profile

import com.ljyh.mei.data.model.response.*
import org.junit.Assert.*
import org.junit.Test

class ListeningArchivePresentationTest {
    @Test fun artistsComeFromRankedSongsWithoutInventedPlayCounts() {
        fun song(id: Long, vararg artists: ArchiveArtistDto) = ListeningTrack(ArchiveSongDto(id, "song", ar = artists.toList()).metadata()!!)
        val one = ArchiveArtistDto(1, "One")
        val two = ArchiveArtistDto(2, "Two")
        val result = frequentArtists(listOf(song(1, two), song(2, one), song(3, one, one), song(4, ArchiveArtistDto(0, ""))))
        assertEquals(listOf("One", "Two"), result.map { it.name })
    }

    @Test fun durationsUseMinutesAsReportedAndKeepHoursReadable() {
        assertEquals("0 分钟", formatListeningDuration(-1))
        assertEquals("59 分钟", formatListeningDuration(59))
        assertEquals("1 小时 30 分钟", formatListeningDuration(90))
    }
}
