package com.ljyh.mei.ui.screen.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ljyh.mei.AppContext
import com.ljyh.mei.data.model.eapi.HomePageResourceShow
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
        if (!hasMore.value || requestJob?.isActive == true) return
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
    /** Returns immediately when a card re-enters composition, avoiding its fallback-color flash. */
    fun getCachedColor(url: String) = colorRepository.getFromMemory(url)

    suspend fun getOrExtractColor(url: String) = colorRepository.getColorOrExtract(context, url)





}
