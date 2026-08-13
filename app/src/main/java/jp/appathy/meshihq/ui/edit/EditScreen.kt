package jp.appathy.meshihq.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import jp.appathy.meshihq.data.db.Shop
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.Budget
import jp.appathy.meshihq.domain.Categories
import jp.appathy.meshihq.domain.ShopStatus
import jp.appathy.meshihq.ui.LocationUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    repository: ShopRepository,
    shopId: Long,
    initialLat: Double,
    initialLon: Double,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(shopId == 0L) }
    var base by remember { mutableStateOf<Shop?>(null) }
    var name by remember { mutableStateOf("") }
    var nameKana by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("その他") }
    var lat by remember { mutableStateOf(if (initialLat != 0.0) initialLat.toString() else "") }
    var lon by remember { mutableStateOf(if (initialLon != 0.0) initialLon.toString() else "") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var openingHours by remember { mutableStateOf("") }
    var closedDays by remember { mutableStateOf("") }
    var lunchMin by remember { mutableStateOf("未設定") }
    var lunchMax by remember { mutableStateOf("未設定") }
    var dinnerMin by remember { mutableStateOf("未設定") }
    var dinnerMax by remember { mutableStateOf("未設定") }
    var memo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("active") }
    var favorite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shopId) {
        if (shopId != 0L) {
            val shop = repository.getShop(shopId)
            if (shop != null) {
                base = shop
                name = shop.name
                nameKana = shop.nameKana.orEmpty()
                category = shop.category
                lat = shop.lat.toString()
                lon = shop.lon.toString()
                address = shop.address.orEmpty()
                phone = shop.phone.orEmpty()
                openingHours = shop.openingHours.orEmpty()
                closedDays = shop.closedDays.orEmpty()
                lunchMin = shop.budgetLunchMin?.toString() ?: "未設定"
                lunchMax = shop.budgetLunchMax?.toString() ?: "未設定"
                dinnerMin = shop.budgetDinnerMin?.toString() ?: "未設定"
                dinnerMax = shop.budgetDinnerMax?.toString() ?: "未設定"
                memo = shop.memo.orEmpty()
                status = shop.status
                favorite = shop.isFavorite
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (shopId == 0L) "店舗を追加" else "店舗を編集") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (shopId != 0L) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.delete(shopId)
                                onDone()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Text("読み込み中", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Field("店名（必須）", name) { name = it }
            Field("よみ", nameKana) { nameKana = it }
            Dropdown("カテゴリ", category, Categories.ALL) { category = it }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    Field("緯度", lat, KeyboardType.Number) { lat = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Field("経度", lon, KeyboardType.Number) { lon = it }
                }
            }
            OutlinedButton(
                onClick = {
                    val here = LocationUtil.lastKnown(context)
                    if (here != null) {
                        lat = here.latitude.toString()
                        lon = here.longitude.toString()
                    } else {
                        error = "現在地を取得できませんでした。地図の長押しで指定してください。"
                    }
                },
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text("現在地を使う")
            }
            Field("住所", address) { address = it }
            Field("電話", phone, KeyboardType.Phone) { phone = it }
            Field("営業時間", openingHours) { openingHours = it }
            Field("定休日", closedDays) { closedDays = it }
            Text("予算（1人あたり）", modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    Dropdown("昼 下限", lunchMin, budgetOptions()) { lunchMin = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Dropdown("昼 上限", lunchMax, budgetOptions()) { lunchMax = it }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    Dropdown("夜 下限", dinnerMin, budgetOptions()) { dinnerMin = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Dropdown("夜 上限", dinnerMax, budgetOptions()) { dinnerMax = it }
                }
            }
            Dropdown("状態", ShopStatus.labelOf(status), ShopStatus.ALL.map { ShopStatus.labelOf(it) }) { label ->
                status = ShopStatus.ALL.first { ShopStatus.labelOf(it) == label }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("お気に入り", modifier = Modifier.weight(1f))
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
            Field("メモ", memo) { memo = it }
            error?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            Button(
                onClick = {
                    val latValue = lat.toDoubleOrNull()
                    val lonValue = lon.toDoubleOrNull()
                    if (name.isBlank()) {
                        error = "店名を入力してください。"
                        return@Button
                    }
                    if (latValue == null || lonValue == null) {
                        error = "座標を入力してください。現在地ボタンか地図の長押しで指定できます。"
                        return@Button
                    }
                    val shop = Shop(
                        id = shopId,
                        name = name.trim(),
                        nameKana = nameKana.blankToNull(),
                        category = category,
                        lat = latValue,
                        lon = lonValue,
                        address = address.blankToNull(),
                        phone = phone.blankToNull(),
                        osmId = base?.osmId,
                        openingHours = openingHours.blankToNull(),
                        openingHoursRaw = base?.openingHoursRaw,
                        closedDays = closedDays.blankToNull(),
                        budgetLunchMin = lunchMin.toBudget(),
                        budgetLunchMax = lunchMax.toBudget(),
                        budgetDinnerMin = dinnerMin.toBudget(),
                        budgetDinnerMax = dinnerMax.toBudget(),
                        memo = memo.blankToNull(),
                        isFavorite = favorite,
                        status = status,
                        createdAt = base?.createdAt ?: 0L,
                        updatedAt = 0L
                    )
                    scope.launch {
                        repository.saveManual(shop)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp)
            ) {
                Text("保存")
            }
        }
    }
}

private fun budgetOptions(): List<String> = listOf("未設定") + Budget.STEPS.map { it.toString() }

private fun String.blankToNull(): String? = trim().takeIf { it.isNotBlank() }

private fun String.toBudget(): Int? = if (this == "未設定") null else toIntOrNull()

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = label != "メモ",
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun Dropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = label)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
