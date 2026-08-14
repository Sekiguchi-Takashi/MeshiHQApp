package jp.appathy.meshihq.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.MenuItem
import jp.appathy.meshihq.data.media.PhotoStore
import jp.appathy.meshihq.data.ocr.MenuCandidate
import jp.appathy.meshihq.data.ocr.MenuOcr
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MenuTab(repository: ShopRepository, shopId: Long, menu: List<MenuItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showManual by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<MenuCandidate>>(emptyList()) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        message = ""
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                val bitmap = PhotoStore.decode(context, uri, 2000)
                if (bitmap == null) emptyList() else MenuOcr.recognize(bitmap)
            }
            reading = false
            if (found.isEmpty()) {
                message = "読み取れる行がありませんでした。明るい場所で、メニュー面が正対するように撮り直すと拾いやすくなります。"
            } else {
                candidates = found
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Button(onClick = { showManual = true }) { Text("手で追加") }
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !reading
            ) {
                Text(if (reading) "読み取り中" else "写真から読み取る")
            }
        }
        if (menu.size > 1) {
            TextButton(
                onClick = {
                    scope.launch {
                        val removed = repository.mergeDuplicateMenu(shopId)
                        message = if (removed == 0) "重複はありませんでした。"
                        else "${removed}件の重複を整理しました。"
                    }
                },
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text("重複を整理")
            }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (menu.isEmpty()) {
            Text("メニューはまだ登録されていません。", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(menu, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (item.price?.let { "${it}円" } ?: "価格未設定") +
                                    " ・ " + SourceType.labelOf(item.sourceType),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { scope.launch { repository.deleteMenuItem(item.id) } }) {
                            Text("削除")
                        }
                    }
                }
            }
        }
    }

    if (showManual) {
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("メニューを追加") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("品名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("価格（円）") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        scope.launch {
                            repository.addMenuItems(
                                shopId,
                                listOf(trimmed to price.trim().toIntOrNull()),
                                SourceType.SELF_VISIT
                            )
                        }
                    }
                    showManual = false
                }) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { showManual = false }) { Text("やめる") }
            }
        )
    }

    if (candidates.isNotEmpty()) {
        val selected = remember(candidates) {
            mutableStateListOf<Boolean>().apply { repeat(candidates.size) { add(true) } }
        }
        AlertDialog(
            onDismissRequest = { candidates = emptyList() },
            title = { Text("読み取り結果 ${candidates.size}件") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(candidates.indices.toList()) { index ->
                        val candidate = candidates[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.getOrElse(index) { false },
                                onCheckedChange = { selected[index] = it }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name)
                                Text(
                                    candidate.price?.let { "${it}円" } ?: "価格なし",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val entries = candidates.filterIndexed { index, _ ->
                        selected.getOrElse(index) { false }
                    }.map { it.name to it.price }
                    scope.launch {
                        repository.addMenuItems(shopId, entries, SourceType.OCR)
                    }
                    candidates = emptyList()
                }) { Text("選んだ分を追加") }
            },
            dismissButton = {
                TextButton(onClick = { candidates = emptyList() }) { Text("やめる") }
            }
        )
    }
}
