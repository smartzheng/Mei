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
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _scanState = MutableStateFlow<LocalScanState>(LocalScanState.Idle)
    val scanState: StateFlow<LocalScanState> = _scanState
    val filter = context.dataStore.data.map { it.localFolderFilter() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalFolderFilter())
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders
    private var scanJob: Job? = null

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
