package jp.appathy.meshihq.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Visit
import jp.appathy.meshihq.data.repo.ShopRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VisitTab(repository: ShopRepository, shopId: Long, visits: List<Visit>) {
    val scope = rememberCoroutineScope()
    val format = remember { SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Button(onClick = { showDialog = true }) { Text("来店を記録") }
        }
        if (visits.isNotEmpty()) {
            val total = visits.mapNotNull { it.amount }.sum()
            val perPerson = visits.filter { it.amount != null && it.people > 0 }
            val average = if (perPerson.isEmpty()) null
            else perPerson.sumOf { it.amount!! / it.people } / perPerson.size
            Text(
                "来店${visits.size}回 ・ 支払い合計${total}円" +
                    (average?.let { " ・ 1人あたり平均${it}円" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        } else {
            Text("来店の記録はまだありません。", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(visits, key = { it.id }) { visit ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(format.format(Date(visit.visitedAt)))
                            Text(
                                buildString {
                                    append("${visit.people}人")
                                    visit.amount?.let { append(" ・ ${it}円") }
                                    visit.rating?.let { append(" ・ 評価${it}") }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (!visit.memo.isNullOrBlank()) {
                                Text(visit.memo, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { scope.launch { repository.deleteVisit(visit.id) } }) {
                            Text("削除")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var people by remember { mutableIntStateOf(1) }
        var rating by remember { mutableIntStateOf(0) }
        var amount by remember { mutableStateOf("") }
        var memo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("来店を記録（今日）") },
            text = {
                Column {
                    Text("人数", style = MaterialTheme.typography.bodySmall)
                    Row {
                        (1..6).forEach { n ->
                            FilterChip(
                                selected = people == n,
                                onClick = { people = n },
                                label = { Text("$n") },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    Text("評価", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Row {
                        (1..5).forEach { n ->
                            FilterChip(
                                selected = rating == n,
                                onClick = { rating = if (rating == n) 0 else n },
                                label = { Text("$n") },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("支払い合計（円）") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = { Text("メモ") },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.addVisit(
                            shopId = shopId,
                            visitedAt = System.currentTimeMillis(),
                            people = people,
                            amount = amount.trim().toIntOrNull(),
                            rating = rating.takeIf { it > 0 },
                            memo = memo.trim().takeIf { it.isNotBlank() }
                        )
                    }
                    showDialog = false
                }) { Text("記録") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("やめる") }
            }
        )
    }
}
