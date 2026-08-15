package jp.appathy.meshihq.ui.import_

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.Categories
import kotlinx.coroutines.launch

/**
 * 取込直後に必ず残る「状態が未確認」「カテゴリがその他」の店を、その場で潰すための行。
 * ここでの修正は手入力扱い（信頼度1.0）なので、以後の取込では上書きされない。
 */
@Composable
fun TidyRow(repository: ShopRepository, shop: Shop, onOpenShop: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(shop.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                shop.category + " ・ " + (shop.address ?: "住所なし"),
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Box {
                    TextButton(onClick = { expanded = true }) { Text("カテゴリ") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Categories.ALL.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    expanded = false
                                    scope.launch { repository.setCategory(shop.id, name) }
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = { scope.launch { repository.setStatus(shop.id, "active") } }) {
                    Text("営業中")
                }
                TextButton(onClick = { scope.launch { repository.setStatus(shop.id, "closed") } }) {
                    Text("閉店")
                }
                TextButton(onClick = { onOpenShop(shop.id) }) { Text("開く") }
            }
        }
    }
}
