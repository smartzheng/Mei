package com.ljyh.mei.ui.screen.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ljyh.mei.data.repository.*
import com.ljyh.mei.utils.preferences.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.data.repository.LocalMusicScanner
import com.ljyh.mei.data.repository.SearchRepository
import com.ljyh.mei.data.network.Resource
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LocalScanState {
    data object Idle : LocalScanState
    data object Scanning : LocalScanState
    data class Done(val count: Int) : LocalScanState
    data class Failed(val message: String) : LocalScanState
}

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val searchRepository: SearchRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _scanState = MutableStateFlow<LocalScanState>(LocalScanState.Idle)
    val scanState: StateFlow<LocalScanState> = _scanState
    val filter = context.dataStore.data.map { it.localFolderFilter() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalFolderFilter())
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders
    private var scanJob: Job? = null
    private val artistRequests = mutableSetOf<String>()
    private val artistRequestLimit = Semaphore(3)
    private val _artistImages = MutableStateFlow<Map<String, String?>>(emptyMap())
    val artistImages: StateFlow<Map<String, String?>> = _artistImages
    private val albumRequests = mutableSetOf<LocalAlbumArtworkKey>()
    private val _albumImages = MutableStateFlow<Map<LocalAlbumArtworkKey, String?>>(emptyMap())
    internal val albumImages: StateFlow<Map<LocalAlbumArtworkKey, String?>> = _albumImages

    fun loadArtistImage(name: String) {
        if (name.isBlank() || name in artistRequests || name in _artistImages.value) return
        artistRequests.add(name)
        viewModelScope.launch {
            try {
                val image = artistRequestLimit.withPermit {
                    when (val result = searchRepository.search(name, type = 100, limit = 20)) {
                        is Resource.Success -> exactArtistImage(name, result.data.artists)
                        else -> null
                    }
                }
                _artistImages.value = _artistImages.value + (name to image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _artistImages.value = _artistImages.value + (name to null)
            } finally {
                artistRequests.remove(name)
            }
        }
    }

    fun loadAlbumImage(title: String, artists: List<String>) {
        val key = LocalAlbumArtworkKey(title.trim(), artists.map(String::trim).filter(String::isNotEmpty).distinct())
        if (key.title.isBlank() || key in albumRequests || key in _albumImages.value) return
        albumRequests.add(key)
        viewModelScope.launch {
            try {
                val image = artistRequestLimit.withPermit {
                    when (val result = searchRepository.search(
                        listOf(key.title, key.artists.joinToString(" ")).filter(String::isNotBlank).joinToString(" "),
                        type = 10,
                        limit = 20,
                    )) {
                        is Resource.Success -> exactAlbumImage(key.title, key.artists, result.data.albums)
                        else -> null
                    }
                }
                _albumImages.value = _albumImages.value + (key to image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _albumImages.value = _albumImages.value + (key to null)
            } finally {
                albumRequests.remove(key)
            }
        }
    }

    fun saveFilter(value: LocalFolderFilter) {
        viewModelScope.launch {
            context.dataStore.edit {
                it[LocalFolderModeKey] = value.mode.name
                it[LocalFolderPathsKey] = value.folders
            }
            scan()
        }
    }

    fun scan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanState.value = LocalScanState.Scanning
            try {
                val result = scanner.scan(context.dataStore.data.first().localFolderFilter())
                _folders.value = result.folders
                _scanState.value = LocalScanState.Done(result.count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _scanState.value = LocalScanState.Failed(error.message ?: "扫描本地音乐失败")
            }
        }
    }
}
