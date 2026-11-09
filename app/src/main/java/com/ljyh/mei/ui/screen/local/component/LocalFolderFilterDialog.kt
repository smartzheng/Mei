package com.ljyh.mei.ui.screen.local.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ljyh.mei.data.repository.LocalFolderFilter
import com.ljyh.mei.data.repository.LocalFolderMode

@Composable
internal fun LocalFolderFilterDialog(
    current: LocalFolderFilter,
    availableFolders: List<String>,
    onDismiss: () -> Unit,
    onSave: (LocalFolderFilter) -> Unit,
) {
    var mode by remember { mutableStateOf(current.mode) }
    var selected by remember { mutableStateOf(current.folders) }
    val folders = remember(availableFolders, current.folders) {
        (availableFolders + current.folders).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地音乐文件夹过滤") },
        text = {
            Column {
                listOf(LocalFolderMode.ALL to "扫描全部文件夹", LocalFolderMode.INCLUDE to "只扫描所选文件夹", LocalFolderMode.EXCLUDE to "排除所选文件夹").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { mode = value }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                Text("选择的文件夹包含其子文件夹；仅列出系统音乐库发现的文件夹。", style = MaterialTheme.typography.bodySmall)
                if (mode != LocalFolderMode.ALL) {
                    if (folders.isEmpty()) Text("未发现文件夹，请先允许访问音频并扫描。", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(folders, key = { it }) { folder ->
                            Row(Modifier.fillMaxWidth().clickable {
                                selected = if (folder in selected) selected - folder else selected + folder
                            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = folder in selected, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(folder, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (mode == LocalFolderMode.INCLUDE && selected.isEmpty()) {
                        Text("请至少选择一个文件夹。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(LocalFolderFilter(mode, selected)) }, enabled = mode != LocalFolderMode.INCLUDE || selected.isNotEmpty()) { Text("保存并扫描") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
