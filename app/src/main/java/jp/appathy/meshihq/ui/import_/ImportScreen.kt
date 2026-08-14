package jp.appathy.meshihq.ui.import_

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.Prefs
import jp.appathy.meshihq.data.remote.OverpassClient
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.ui.LocationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    repository: ShopRepository,
    centerLat: Double,
    centerLon: Double,
    onOpenShop: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingFlow = remember { repository.observePendingChanges() }
    val pending by pendingFlow.collectAsState(initial = emptyList())
    var radius by remember { mutableIntStateOf(1000) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val center = remember(centerLat, centerLon) {
        if (centerLat != 0.0 && centerLon != 0.0) {
            centerLat to centerLon
        } else {
            val here = LocationUtil.lastKnown(context)
            if (here != null) here.latitude to here.longitude
            else Prefs.defaultLat(context) to Prefs.defaultLon(context)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("取込・承認") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "中心 %.5f, %.5f".format(center.first, center.second),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    listOf(500, 1000, 1500, 2000).forEach { value ->
                        FilterChip(
                            selected = radius == value,
                            onClick = { radius = value },
                            label = { Text("${value}m") },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                Button(
                    enabled = !running,
                    onClick = {
                        val since = System.currentTimeMillis() - Prefs.lastImportAt(context)
                        if (since < 60_000) {
                            message = "前回の取込から${since / 1000}秒しか経っていません。" +
                                "Overpassは公共サーバなので、1分ほど空けてから実行してください。"
                        } else {
                        running = true
                        message = ""
                        scope.launch {
                            val result = try {
                                withContext(Dispatchers.IO) {
                                    val candidates = OverpassClient.fetchAround(
                                        center.first,
                                        center.second,
                                        radius
                                    )
                                    repository.importFromOsm(candidates) to candidates.size
                                }
                            } catch (e: Exception) {
                                null
                            }
                            running = false
                            Prefs.setLastImportAt(context, System.currentTimeMillis())
                            message = if (result == null) {
                                "取込に失敗しました。通信状況を確認して、少し時間をおいて再試行してください。"
                            } else {
                                val (summary, found) = result
                                "候補${found}件：新規${summary.added} / 更新${summary.updated} / " +
                                    "承認待ち${summary.pending} / 変更なし${summary.untouched}"
                            }
                        }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("この範囲の飲食店を取り込む")
                }
                if (running) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text("OpenStreetMapに問い合わせ中")
                    }
                }
                if (message.isNotBlank()) {
                    Text(message, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    "自分で入力した値（信頼度1.0）はOSMの値で上書きされず、下の承認待ちに入ります。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            HorizontalDivider()
            Text(
                if (pending.isEmpty()) "承認待ちはありません" else "承認待ち ${pending.size}件",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pending, key = { it.id }) { change ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(change.fieldName, style = MaterialTheme.typography.titleSmall)
                            Text("現在: " + (change.currentValue ?: "-"))
                            Text("提案: " + (change.proposedValue ?: "-"))
                            change.reason?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { scope.launch { repository.approve(change.id) } }) {
                                    Text("承認")
                                }
                                OutlinedButton(
                                    onClick = { scope.launch { repository.reject(change.id) } },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("却下")
                                }
                                TextButton(
                                    onClick = { onOpenShop(change.shopId) },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("店舗を見る")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
