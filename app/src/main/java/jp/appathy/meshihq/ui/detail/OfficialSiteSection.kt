package jp.appathy.meshihq.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.remote.OfficialSiteClient
import jp.appathy.meshihq.data.remote.SiteField
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 公式サイトのURLを登録し、そのページから住所・電話・営業時間・座標の候補を拾う。
 * 反映は取込と同じ判定を通すので、手入力済みの値は承認待ちに入る。
 */
@Composable
fun OfficialSiteSection(repository: ShopRepository, shop: Shop) {
    val scope = rememberCoroutineScope()
    var url by remember(shop.id, shop.website) { mutableStateOf(shop.website.orEmpty()) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf<List<SiteField>>(emptyList()) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("公式サイトURL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { scope.launch { repository.setWebsite(shop.id, url) } }) {
                Text("URLを保存")
            }
            OutlinedButton(
                enabled = !running && url.startsWith("http"),
                onClick = {
                    running = true
                    message = ""
                    scope.launch {
                        repository.setWebsite(shop.id, url)
                        val found = try {
                            withContext(Dispatchers.IO) { OfficialSiteClient.fetch(url.trim()) }
                        } catch (e: Exception) {
                            null
                        }
                        running = false
                        when {
                            found == null -> message = "取得できませんでした。URLを確認してください。"
                            found.isEmpty() -> message = "住所や営業時間らしき記述が見つかりませんでした。"
                            else -> fields = found
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(if (running) "取得中" else "サイトから取得")
            }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (fields.isNotEmpty()) {
        val selected = remember(fields) {
            mutableStateListOf<Boolean>().apply { repeat(fields.size) { add(true) } }
        }
        AlertDialog(
            onDismissRequest = { fields = emptyList() },
            title = { Text("公式サイトの読み取り結果") },
            text = {
                Column {
                    fields.forEachIndexed { index, field ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.getOrElse(index) { false },
                                onCheckedChange = { selected[index] = it }
                            )
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(field.label, style = MaterialTheme.typography.bodySmall)
                                Text(field.value)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = fields.filterIndexed { index, _ ->
                        selected.getOrElse(index) { false }
                    }
                    scope.launch {
                        val values = chosen
                            .filter { it.fieldName != "geo" }
                            .map { it.fieldName to it.value }
                        repository.applyExternalValues(shop.id, values, SourceType.OFFICIAL)
                        chosen.firstOrNull { it.fieldName == "geo" }?.let { geo ->
                            val parts = geo.value.split(",")
                            val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
                            val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                repository.moveShop(shop.id, lat, lon)
                            }
                        }
                    }
                    fields = emptyList()
                }) { Text("選んだ分を反映") }
            },
            dismissButton = {
                TextButton(onClick = { fields = emptyList() }) { Text("やめる") }
            }
        )
    }
}
