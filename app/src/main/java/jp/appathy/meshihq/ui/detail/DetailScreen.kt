package jp.appathy.meshihq.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Collection
import jp.appathy.meshihq.data.db.FactSource
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.Budget
import jp.appathy.meshihq.domain.OpeningHours
import jp.appathy.meshihq.domain.ShopStatus
import jp.appathy.meshihq.domain.SourceType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repository: ShopRepository,
    shopId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val shopFlow = remember(shopId) { repository.observeShop(shopId) }
    val factsFlow = remember(shopId) { repository.observeFacts(shopId) }
    val visitsFlow = remember(shopId) { repository.observeVisits(shopId) }
    val collectionsFlow = remember { repository.observeCollections() }
    val memberFlow = remember(shopId) { repository.observeCollectionIds(shopId) }
    val photosFlow = remember(shopId) { repository.observePhotos(shopId) }
    val menuFlow = remember(shopId) { repository.observeMenu(shopId) }
    val shop by shopFlow.collectAsState(initial = null)
    val facts by factsFlow.collectAsState(initial = emptyList())
    val photos by photosFlow.collectAsState(initial = emptyList())
    val menu by menuFlow.collectAsState(initial = emptyList())
    val visits by visitsFlow.collectAsState(initial = emptyList())
    val collections by collectionsFlow.collectAsState(initial = emptyList())
    val memberIds by memberFlow.collectAsState(initial = emptyList())
    var tabIndex by remember { mutableIntStateOf(0) }
    var people by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shop?.name ?: "店舗") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    val current = shop
                    if (current != null) {
                        IconButton(onClick = { scope.launch { repository.toggleFavorite(current) } }) {
                            Icon(
                                if (current.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "お気に入り"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(shopId) }) {
                Icon(Icons.Filled.Edit, contentDescription = "編集")
            }
        }
    ) { padding ->
        val current = shop
        if (current == null) {
            Text("店舗が見つかりません", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("情報") })
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("メニュー" + if (menu.isEmpty()) "" else " ${menu.size}") }
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("写真" + if (photos.isEmpty()) "" else " ${photos.size}") }
                )
                Tab(
                    selected = tabIndex == 3,
                    onClick = { tabIndex = 3 },
                    text = { Text("来店" + if (visits.isEmpty()) "" else " ${visits.size}") }
                )
                Tab(selected = tabIndex == 4, onClick = { tabIndex = 4 }, text = { Text("根拠") })
            }
            when (tabIndex) {
                0 -> InfoTab(
                    repository = repository,
                    shop = current,
                    people = people,
                    collections = collections,
                    memberIds = memberIds,
                    onPeopleChange = { people = it },
                    onToggleCollection = { collectionId, member ->
                        scope.launch {
                            repository.setCollectionMembership(collectionId, shopId, member)
                        }
                    }
                )
                1 -> MenuTab(repository, shopId, menu)
                2 -> PhotoTab(repository, shopId, photos)
                3 -> VisitTab(repository, shopId, visits)
                else -> FactTab(facts)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTab(
    repository: ShopRepository,
    shop: Shop,
    people: Int,
    collections: List<Collection>,
    memberIds: List<Long>,
    onPeopleChange: (Int) -> Unit,
    onToggleCollection: (Long, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(shop.category + " ・ " + ShopStatus.labelOf(shop.status), style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        InfoRow("住所", shop.address)
        InfoRow("電話", shop.phone)
        InfoRow("営業時間", OpeningHours.format(shop.openingHours) ?: shop.openingHoursRaw)
        InfoRow("定休日", shop.closedDays)
        InfoRow("座標", "%.5f, %.5f".format(shop.lat, shop.lon))
        InfoRow("公式サイト", shop.website)
        OfficialSiteSection(repository, shop)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("人数", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..6).forEach { n ->
                FilterChip(
                    selected = people == n,
                    onClick = { onPeopleChange(n) },
                    label = { Text("${n}人") }
                )
            }
        }
        InfoRow(
            "予算（昼）",
            Budget.label(shop.budgetLunchMin, shop.budgetLunchMax) +
                " → 合計 " + Budget.total(shop.budgetLunchMin, shop.budgetLunchMax, people)
        )
        InfoRow(
            "予算（夜）",
            Budget.label(shop.budgetDinnerMin, shop.budgetDinnerMax) +
                " → 合計 " + Budget.total(shop.budgetDinnerMin, shop.budgetDinnerMax, people)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("コレクション", style = MaterialTheme.typography.titleSmall)
        if (collections.isEmpty()) {
            Text(
                "記録タブでコレクションを作ると、ここから登録できます。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            collections.forEach { collection ->
                val member = memberIds.contains(collection.id)
                FilterChip(
                    selected = member,
                    onClick = { onToggleCollection(collection.id, !member) },
                    label = { Text(collection.name) }
                )
            }
        }
        if (!shop.memo.isNullOrBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("メモ", style = MaterialTheme.typography.titleSmall)
            Text(shop.memo, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FactTab(facts: List<FactSource>) {
    val format = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    if (facts.isEmpty()) {
        Text("根拠の記録はまだありません。", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(facts, key = { it.id }) { fact ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(fact.fieldName, style = MaterialTheme.typography.titleSmall)
                    Text(fact.value ?: "-", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        SourceType.labelOf(fact.sourceType) +
                            " ・ 信頼度 " + fact.confidence +
                            " ・ " + format.format(Date(fact.observedAt)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
