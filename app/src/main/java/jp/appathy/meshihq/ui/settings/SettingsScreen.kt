package jp.appathy.meshihq.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.Prefs
import jp.appathy.meshihq.data.export.BonsaiExport
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.ui.LocationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: ShopRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var radius by remember { mutableIntStateOf(Prefs.radiusMeters(context)) }
    var message by remember { mutableStateOf("") }
    var hotPepperKey by remember { mutableStateOf(Prefs.hotPepperKey(context)) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                val shops = repository.getAllShops()
                val menus = shops.associate { it.id to repository.getMenu(it.id) }
                BonsaiExport.exportAll(context, uri, shops, menus)
            }
            message = "${count}件をBonsai資料形式（frontmatter付きMarkdown）とshops.jsonで書き出しました。"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext 0
                val shops = BonsaiExport.fromJson(text)
                shops.forEach { repository.saveManual(it) }
                shops.size
            }
            message = "${count}件を取り込みました。"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("設定") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("近い順の対象半径", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf(500, 1000, 1500, 2000, 3000).forEach { value ->
                    FilterChip(
                        selected = radius == value,
                        onClick = {
                            radius = value
                            Prefs.setRadiusMeters(context, value)
                        },
                        label = { Text("${value}m") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Button(
                onClick = {
                    val here = LocationUtil.lastKnown(context)
                    if (here != null) {
                        Prefs.setDefaultCenter(context, here.latitude, here.longitude)
                        message = "地図の初期表示位置を現在地に設定しました。"
                    } else {
                        message = "現在地を取得できませんでした。"
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("地図の初期表示位置を現在地にする")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("ホットペッパーAPIキー", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = hotPepperKey,
                onValueChange = {
                    hotPepperKey = it
                    Prefs.setHotPepperKey(context, it)
                },
                label = { Text("リクルートWEBサービスのキー") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Text(
                "webservice.recruit.co.jp で無料発行できます。キーはこの端末内だけに保存されます。",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("データ", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = { exportLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("フォルダを選んで書き出し")
            }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("shops.json から取り込み")
            }
            if (message.isNotBlank()) {
                Text(message, modifier = Modifier.padding(top = 12.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "書き出し先フォルダをBonsaiのRAG同期フォルダに合わせておくと、店舗資料がそのまま検索対象になります。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
