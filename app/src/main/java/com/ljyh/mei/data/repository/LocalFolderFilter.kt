package com.ljyh.mei.data.repository

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.Preferences

enum class LocalFolderMode { ALL, INCLUDE, EXCLUDE }

data class LocalFolderFilter(val mode: LocalFolderMode = LocalFolderMode.ALL, val folders: Set<String> = emptySet()) {
    fun allows(path: String?): Boolean {
        val candidate = path.orEmpty().trim().trim('/')
        val matched = folders.any { raw ->
            val folder = raw.trim().trim('/')
            folder.isNotEmpty() && (candidate.equals(folder, ignoreCase = true) ||
                candidate.startsWith("$folder/", ignoreCase = true))
        }
        return when (mode) {
            LocalFolderMode.ALL -> true
            LocalFolderMode.INCLUDE -> matched
            LocalFolderMode.EXCLUDE -> !matched
        }
    }
}

val LocalFolderModeKey = stringPreferencesKey("local_music_folder_mode")
val LocalFolderPathsKey = stringSetPreferencesKey("local_music_folder_paths")

fun Preferences.localFolderFilter(): LocalFolderFilter = LocalFolderFilter(
    mode = LocalFolderMode.entries.firstOrNull { it.name == this[LocalFolderModeKey] } ?: LocalFolderMode.ALL,
    folders = this[LocalFolderPathsKey].orEmpty(),
)
