package com.ljyh.mei.data.model.response

import com.ljyh.mei.data.model.domain.MediaMetadata
import kotlinx.serialization.Serializable

@Serializable
data class ListeningRankResponse(
    val code: Int = 0,
    val weekData: List<ListeningRecordDto>? = null,
    val allData: List<ListeningRecordDto>? = null,
) {
    fun tracks(allTime: Boolean): List<ListeningTrack> {
        check(code == 200) { "听歌排行暂时无法获取" }
        val records = if (allTime) allData else weekData
        checkNotNull(records) { "听歌排行暂时不可用" }
        return records.mapNotNull { record -> record.song?.metadata()?.let {
            ListeningTrack(it, record.playCount?.takeIf { count -> count >= 0 })
        } }.distinctBy { it.song.id }
    }
}

@Serializable
data class ListeningRecordDto(val song: ArchiveSongDto? = null, val playCount: Int? = null)

@Serializable
data class RecentListeningResponse(val code: Int = 0, val data: RecentListeningData? = null) {
    fun tracks(): List<ListeningTrack> {
        check(code == 200) { "最近收听暂时无法获取" }
        val records = checkNotNull(data?.list) { "最近收听暂时不可用" }
        return records.mapNotNull { record -> record.data?.metadata()?.let {
            ListeningTrack(it, playedAt = record.playTime?.takeIf { time -> time > 0 }?.let { time ->
                if (time < 100_000_000_000L) time * 1_000L else time
            })
        } }.sortedByDescending { it.playedAt ?: 0 }.distinctBy { it.song.id }
    }
}

@Serializable
data class RecentListeningData(val list: List<RecentListeningDto>? = null)
@Serializable
data class RecentListeningDto(val data: ArchiveSongDto? = null, val playTime: Long? = null)

/** Both catalogue song shapes occur in the rank and cloud-history responses. */
@Serializable
data class ArchiveSongDto(
    val id: Long = 0, val name: String = "",
    val al: ArchiveAlbumDto? = null, val album: ArchiveAlbumDto? = null,
    val ar: List<ArchiveArtistDto> = emptyList(), val artists: List<ArchiveArtistDto> = emptyList(),
    val dt: Long? = null, val duration: Long? = null,
) {
    fun metadata(): MediaMetadata? {
        if (id <= 0 || name.isBlank()) return null
        val release = al ?: album
        return MediaMetadata(id, name, release?.picUrl.orEmpty(),
            ar.ifEmpty { artists }.map { MediaMetadata.Artist(it.id, it.name, it.picUrl) },
            dt ?: duration ?: 0, MediaMetadata.Album(release?.id ?: 0, release?.name.orEmpty()))
    }
}
@Serializable
data class ArchiveAlbumDto(val id: Long = 0, val name: String = "", val picUrl: String? = null)
@Serializable
data class ArchiveArtistDto(val id: Long = 0, val name: String = "", val picUrl: String? = null)

data class ListeningTrack(val song: MediaMetadata, val playCount: Int? = null, val playedAt: Long? = null)
