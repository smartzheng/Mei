package com.ljyh.mei.ui.screen.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.AppContext
import com.ljyh.mei.data.model.eapi.HomePageResourceShow
import com.ljyh.mei.data.model.domain.toMediaItem
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.data.repository.HomeRepository
import com.ljyh.mei.di.repository.ColorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val playlists: com.ljyh.mei.data.repository.PlaylistRepository,
    private val colorRepository: ColorRepository
) : ViewModel() {
    val context= AppContext.instance

    private val _homePageResourceShow =
        MutableStateFlow<Resource<List<HomePageResourceShow.Data.Block>>>(Resource.Loading)
    val homePageResourceShow: StateFlow<Resource<List<HomePageResourceShow.Data.Block>>> = _homePageResourceShow


    private var feed: HomePageResourceShow.Data? = null
    private var requestJob: kotlinx.coroutines.Job? = null
    private var account: String? = null
    val loadingMore = MutableStateFlow(false)
    val loadMoreError = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val refreshError = MutableStateFlow(false)
    val hasMore = MutableStateFlow(false)

    fun homePageResourceShow(refresh: Boolean = false, userId: String = account.orEmpty()) {
        val changed = account != null && account != userId
        if (!changed && !refresh && _homePageResourceShow.value is Resource.Success) return
        if (!changed && !refresh && requestJob?.isActive == true) return
        account = userId
        requestJob?.cancel()
        if (changed) {
            feed = null
            _homePageResourceShow.value = Resource.Loading
        }
        loadingMore.value = false
        loadMoreError.value = false
        refreshError.value = false
        requestJob = viewModelScope.launch {
            try {
                refreshing.value = true
                when (val result = repository.getHomePageResourceShow(refresh || changed)) {
                    is Resource.Success -> {
                        feed = result.data
                        hasMore.value = result.data.hasMore
                        _homePageResourceShow.value = Resource.Success(result.data.blocks)
                    }
                    is Resource.Error -> {
                        if (feed == null) _homePageResourceShow.value = result
                        else refreshError.value = true
                    }
                    else -> Unit
                }
            } finally { refreshing.value = false }
        }
    }

    fun loadMore() {
        val previous = feed ?: return
        if (!hasMore.value || refreshing.value || loadingMore.value) return
        loadingMore.value = true
        loadMoreError.value = false
        requestJob = viewModelScope.launch {
            try {
                when (val result = repository.loadMore(previous)) {
                    is Resource.Success -> {
                        feed = result.data
                        hasMore.value = result.data.hasMore
                        _homePageResourceShow.value = Resource.Success(result.data.blocks)
                    }
                    is Resource.Error -> loadMoreError.value = true
                    else -> Unit
                }
            } finally { loadingMore.value = false }
        }
    }
    val preparingPlaylistId = MutableStateFlow<String?>(null)

    suspend fun preparePlaylistQueue(id: String): Resource<com.ljyh.mei.playback.queue.ListQueue> {
        if (preparingPlaylistId.value != null) return Resource.Loading
        preparingPlaylistId.value = id
        return try {
            when (val detail = playlists.getPlaylistDetail(id)) {
                is Resource.Success -> {
                    val playlist = detail.data
                    val ids = playlist.trackIds.ifEmpty { playlist.tracks.map { it.id } }.distinct()
                    if (ids.isEmpty()) Resource.Error("歌单暂无可播放歌曲")
                    else {
                        val known = playlist.tracks.associate { it.id.toString() to it.toMediaItem() }
                        Resource.Success(com.ljyh.mei.playback.queue.ListQueue(
                            id = "playlist_$id", title = playlist.name,
                            source = com.ljyh.mei.playback.queue.QueueSource(com.ljyh.mei.playback.queue.QueueSource.Type.PLAYLIST, id, playlist.name),
                            items = ids.map { it.toString() to known[it.toString()] },
                        ))
                    }
                }
                is Resource.Error -> Resource.Error("暂时无法加载歌单，请稍后重试")
                else -> Resource.Loading
            }
        } finally { preparingPlaylistId.value = null }
    }

    private val playlistNames = mutableMapOf<String, String>()
    fun cachedMemoryPlaylistName(id: String): String? = playlistNames[id]

    suspend fun memoryPlaylistName(id: String): String? {
        playlistNames[id]?.let { return it }
        val response = playlists.getPlaylistName(id)
        return (response as? Resource.Success)?.data?.also { playlistNames[id] = it }
    }

    suspend fun artistAlbumId(type: String, id: String): Resource<String> =
        com.ljyh.mei.data.network.safeApiCall {
            requireNotNull(com.ljyh.mei.data.model.eapi.resolveHomeArtistAlbum(type, id) { songId ->
                playlists.getSongDetails(listOf(songId)).firstOrNull()?.album?.id
            }) { "暂时无法获取所属专辑" }
        }

    /** Returns immediately when a card re-enters composition, avoiding its fallback-color flash. */
    fun getCachedColor(url: String) = colorRepository.getFromMemory(url)

    suspend fun getOrExtractColor(url: String) = colorRepository.getColorOrExtract(context, url)





}
