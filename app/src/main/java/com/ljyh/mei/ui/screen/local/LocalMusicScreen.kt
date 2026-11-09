package com.ljyh.mei.ui.screen.local

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import java.net.URLEncoder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ljyh.mei.di.AppDatabase
import com.ljyh.mei.ui.local.LocalNavController
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.backToMain
import com.ljyh.mei.ui.screen.local.component.AlbumRow
import com.ljyh.mei.ui.screen.local.component.ArtistRow
import com.ljyh.mei.ui.screen.local.component.EmptyLocalMusic
import com.ljyh.mei.ui.screen.local.component.FolderItem
import com.ljyh.mei.ui.screen.local.component.LocalFolderFilterDialog
import com.ljyh.mei.ui.screen.local.component.ManagementCard
import com.ljyh.mei.ui.screen.local.component.SectionHeader
import com.ljyh.mei.ui.component.glass.MeiHazeTopBar
import com.ljyh.mei.ui.component.glass.MeiGlassIconButton
import com.ljyh.mei.ui.component.layout.meiPageGutter
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val pageHaze = rememberHazeState()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val contentTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val listState = rememberLazyListState()
    val collapseDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val backgroundProgress by remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val scanState by viewModel.scanState.collectAsState()
    val folderFilter by viewModel.filter.collectAsState()
    val availableFolders by viewModel.folders.collectAsState()
    val artistImages by viewModel.artistImages.collectAsState()
    val albumImages by viewModel.albumImages.collectAsState()
    var showFolderFilter by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember(permission) {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
    }
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) viewModel.scan()
    }
    val requestScan = {
        if (hasAudioPermission) viewModel.scan() else permissionLauncher.launch(permission)
    }

    val allLocalSongs by db.songDao().getLocalSongs().collectAsState(initial = emptyList())
    val localSongs = remember(allLocalSongs, folderFilter) { allLocalSongs.filter { folderFilter.allows(it.folderPath) } }
    val albums = remember(localSongs) { localSongs.map { it.album }.filter { it.isNotBlank() }.distinct().sorted() }
    if (showFolderFilter) {
        LocalFolderFilterDialog(folderFilter, availableFolders, onDismiss = { showFolderFilter = false }, onSave = {
            showFolderFilter = false
            viewModel.saveFilter(it)
        })
    }

    val artists = remember(localSongs) {
        localSongs.flatMap { it.artist }.distinct().sorted()
    }

    val folders = remember(localSongs) {
        localSongs.groupBy { it.folderPath ?: "" }
            .filter { it.key.isNotBlank() }
            .map { (path, songs) ->
                val name = path.substringAfterLast('/').ifEmpty { path }
                path to FoldInfo(name, songs.size, songs.firstOrNull { it.cover.isNotEmpty() }?.cover)
            }
            .sortedBy { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .hazeSource(pageHaze)
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            if (scanState is LocalScanState.Scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = contentTop))
            }
            if (scanState is LocalScanState.Failed) {
                Text(
                    (scanState as LocalScanState.Failed).message,
                    modifier = Modifier.padding(horizontal = meiPageGutter()).padding(top = contentTop + 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (localSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = contentTop),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyLocalMusic(
                        hasPermission = hasAudioPermission,
                        isScanning = scanState is LocalScanState.Scanning,
                        onScan = requestScan,
                        filtered = folderFilter.mode != com.ljyh.mei.data.repository.LocalFolderMode.ALL,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = contentTop, bottom = 16.dp)
                ) {
                    item { SectionHeader("歌曲", "${localSongs.size} 首") }
                    item {
                        ManagementCard(
                            "全部歌曲",
                            "浏览所有本地歌曲",
                            Icons.Rounded.MusicNote,
                            onClick = {
                                Screen.LocalSongList.navigate(navController) {
                                    addPath("all"); addPath("all")
                                }
                            }
                        )
                    }

                    if (artists.isNotEmpty()) {
                        item { SectionHeader("艺术家", "${artists.size} 位艺术家") }
                        item {
                            ArtistRow(
                                artists = artists,
                                songs = localSongs,
                                artistImages = artistImages,
                                onArtistVisible = viewModel::loadArtistImage,
                                onArtistClick = { name ->
                                    Screen.LocalSongList.navigate(navController) {
                                        addPath("artist"); addPath(Uri.encode(name))
                                    }
                                }
                            )
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item { SectionHeader("专辑", "${albums.size} 张专辑") }
                        item {
                            AlbumRow(
                                albums = albums,
                                songs = localSongs,
                                albumImages = albumImages,
                                onAlbumVisible = viewModel::loadAlbumImage,
                                onAlbumClick = { name ->
                                    Screen.LocalSongList.navigate(navController) {
                                        addPath("album"); addPath(Uri.encode(name))
                                    }
                                }
                            )
                        }
                    }

                    if (folders.isNotEmpty()) {
                        item { SectionHeader("文件夹", "${folders.size} 个文件夹") }
                        items(folders, key = { it.first }) { (path, info) ->
                            FolderItem(
                                folderPath = path,
                                folderName = info.name,
                                songCount = info.count,
                                coverUrl = info.coverUrl,
                                onClick = {
                                    Screen.LocalSongList.navigate(navController) {
                                        addPath("folder"); addPath(URLEncoder.encode(path, "UTF-8"))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        MeiHazeTopBar(
            state = pageHaze,
            title = "本地音乐",
            progress = 1f,
            backgroundProgress = backgroundProgress,
            onNavigateBack = navController::navigateUp,
            onBackLongClick = navController::backToMain,
            actions = {
                MeiGlassIconButton(
                    onClick = { showFolderFilter = true },
                    enabled = hasAudioPermission && scanState !is LocalScanState.Scanning,
                ) {
                    Icon(Icons.Rounded.FilterAlt, contentDescription = "文件夹过滤")
                }
                MeiGlassIconButton(
                    onClick = requestScan,
                    enabled = scanState !is LocalScanState.Scanning,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "扫描本地音乐")
                }
            },
        )
    }
}

private data class FoldInfo(
    val name: String,
    val count: Int,
    val coverUrl: String?
)
