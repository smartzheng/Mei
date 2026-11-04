package com.ljyh.mei.ui.screen.main.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.ljyh.mei.data.network.Resource
import com.ljyh.mei.ui.local.LocalPlayerConnection
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberHomePlaylistPlayback(viewModel: HomeViewModel): (String) -> Unit {
    val connection = LocalPlayerConnection.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { id ->
        if (connection != null && id.isNotBlank()) scope.launch {
            when (val result = viewModel.preparePlaylistQueue(id)) {
                is Resource.Success -> connection.playQueue(result.data)
                is Resource.Error -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }
}
