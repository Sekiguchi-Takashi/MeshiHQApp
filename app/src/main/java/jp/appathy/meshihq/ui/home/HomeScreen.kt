package jp.appathy.meshihq.ui.home

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.Prefs
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.Budget
import jp.appathy.meshihq.domain.Geo
import jp.appathy.meshihq.ui.LocationUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ShopRepository,
    onOpenShop: (Long) -> Unit,
    onNewShop: () -> Unit
) {
    val context = LocalContext.current
    var keyword by remember { mutableStateOf("") }
    val flow = remember(keyword) { repository.searchShops(keyword) }
    val shops by flow.collectAsState(initial = emptyList())
    var location by remember { mutableStateOf<Location?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        location = LocationUtil.lastKnown(context)
    }

    LaunchedEffect(Unit) {
        if (LocationUtil.hasPermission(context)) {
            location = LocationUtil.lastKnown(context)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val radius = Prefs.radiusMeters(context)
    val favorites = shops.filter { it.isFavorite }
    val nearby = if (location != null) {
        shops.map { it to Geo.distanceMeters(location!!.latitude, location!!.longitude, it.lat, it.lon) }
            .sortedBy { it.second }
            .filter { it.second <= radius * 4 }
    } else {
        shops.map { it to -1.0 }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("飯HQ") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewShop) {
                Icon(Icons.Filled.Add, contentDescription = "店舗を追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("店名・カテゴリで検索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
            ) {
                if (favorites.isNotEmpty()) {
                    item { SectionTitle("お気に入り") }
                    items(favorites, key = { "fav" + it.id }) { shop ->
                        ShopRow(shop, null) { onOpenShop(shop.id) }
                    }
                }
                item {
                    SectionTitle(if (location != null) "近い順" else "最近更新した順")
                }
                if (shops.isEmpty()) {
                    item {
                        Text(
                            "まだ店舗がありません。右下の＋か、地図の長押しで登録できます。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(nearby, key = { it.first.id }) { pair ->
                    ShopRow(pair.first, pair.second.takeIf { it >= 0 }) { onOpenShop(pair.first.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ShopRow(shop: Shop, distance: Double?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(shop.name, style = MaterialTheme.typography.titleMedium)
                if (shop.isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "お気に入り")
                }
            }
            Text(
                buildString {
                    append(shop.category)
                    if (distance != null) {
                        append(" ・ ")
                        append(Geo.distanceLabel(distance))
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "昼 " + Budget.label(shop.budgetLunchMin, shop.budgetLunchMax) +
                    " / 夜 " + Budget.label(shop.budgetDinnerMin, shop.budgetDinnerMax),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
