package jp.appathy.meshihq.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.repo.ShopRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repository: ShopRepository, onOpenShop: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val shopsFlow = remember { repository.observeShops() }
    val visitsFlow = remember { repository.observeAllVisits() }
    val collectionsFlow = remember { repository.observeCollections() }
    val countsFlow = remember { repository.observeCollectionCounts() }
    val shops by shopsFlow.collectAsState(initial = emptyList())
    val visits by visitsFlow.collectAsState(initial = emptyList())
    val collections by collectionsFlow.collectAsState(initial = emptyList())
    val counts by countsFlow.collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    var openedCollection by remember { mutableStateOf<Long?>(null) }
    var members by remember { mutableStateOf<List<Shop>>(emptyList()) }

    var period by remember { mutableStateOf("all") }
    val monthFormat = remember { SimpleDateFormat("yyyy/MM", Locale.JAPAN) }
    val periodStart = remember(period) {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        when (period) {
            "month" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            "year" -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            else -> 0L
        }
    }
    val shopNames = remember(shops) { shops.associate { it.id to it.name } }
    val byCategory = remember(shops) {
        shops.groupBy { it.category }.map { it.key to it.value.size }.sortedByDescending { it.second }
    }
    val scoped = remember(visits, periodStart) { visits.filter { it.visitedAt >= periodStart } }
    val byMonth = remember(scoped) {
        scoped.groupBy { monthFormat.format(Date(it.visitedAt)) }
            .map { it.key to it.value.size }
            .sortedByDescending { it.first }
            .take(6)
    }
    val frequent = remember(scoped) {
        scoped.groupBy { it.shopId }.map { it.key to it.value.size }
            .sortedByDescending { it.second }.take(5)
    }
    val totalSpend = remember(scoped) { scoped.mapNotNull { it.amount }.sum() }

    LaunchedEffect(openedCollection) {
        val id = openedCollection
        members = if (id == null) emptyList() else repository.shopsInCollection(id)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("記録") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "登録 ${shops.size}店 ・ 来店 ${scoped.size}回",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("支払い合計 ${totalSpend}円", style = MaterialTheme.typography.bodyMedium)
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            listOf("month" to "今月", "year" to "今年", "all" to "全期間").forEach { option ->
                                FilterChip(
                                    selected = period == option.first,
                                    onClick = { period = option.first },
                                    label = { Text(option.second) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Section("カテゴリ別") }
            items(byCategory) { (category, count) ->
                BarRow(category, count, byCategory.firstOrNull()?.second ?: 1)
            }
            item { Section("月別の来店") }
            if (byMonth.isEmpty()) {
                item { Text("来店の記録がありません。", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(byMonth) { (month, count) ->
                BarRow(month, count, byMonth.maxOfOrNull { it.second } ?: 1)
            }
            item { Section("よく行く店") }
            items(frequent) { (shopId, count) ->
                Text(
                    (shopNames[shopId] ?: "不明") + " ${count}回",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenShop(shopId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("コレクション", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showCreate = true }) { Text("作成") }
                }
            }
            items(collections) { collection ->
                val count = counts.firstOrNull { it.collectionId == collection.id }?.count ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openedCollection = collection.id }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(collection.name + " ${count}店", modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { repository.deleteCollection(collection.id) } }) {
                        Text("削除")
                    }
                }
            }
            item {
                Text(
                    "コレクションへの登録は、店舗詳細の情報タブから行います。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("コレクションを作成") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名前（例: 接待用、ひとり飯）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.createCollection(name) }
                    showCreate = false
                }) { Text("作成") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("やめる") } }
        )
    }

    val opened = openedCollection
    if (opened != null) {
        AlertDialog(
            onDismissRequest = { openedCollection = null },
            title = { Text(collections.firstOrNull { it.id == opened }?.name ?: "コレクション") },
            text = {
                Column {
                    if (members.isEmpty()) Text("まだ店舗が入っていません。")
                    members.forEach { shop ->
                        Text(
                            shop.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openedCollection = null
                                    onOpenShop(shop.id)
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openedCollection = null }) { Text("閉じる") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun BarRow(label: String, count: Int, max: Int) {
    val ratio = if (max <= 0) 0f else count.toFloat() / max
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.weight(0.6f), verticalAlignment = Alignment.CenterVertically) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (ratio < 0.05f) 0.05f else ratio)
                    .padding(end = 6.dp)
            ) {
                Text(" ", style = MaterialTheme.typography.bodySmall)
            }
            Text("$count", style = MaterialTheme.typography.bodySmall)
        }
    }
}
