package jp.appathy.meshihq.ui.detail

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.appathy.meshihq.data.db.Photo
import jp.appathy.meshihq.data.media.PhotoStore
import jp.appathy.meshihq.data.repo.ShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoTab(repository: ShopRepository, shopId: Long, photos: List<Photo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("shop") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { PhotoStore.save(context, uri, shopId) }
            if (path != null) repository.addPhoto(shopId, path, kind)
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    kind = "shop"
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) { Text("外観・料理") }
            Button(
                enabled = !busy,
                onClick = {
                    kind = "menu"
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) { Text("メニュー表") }
        }
        if (photos.isEmpty()) {
            Text(
                "写真はまだありません。カメラで撮ってからギャラリー経由で選びます。",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                Card(modifier = Modifier.padding(4.dp)) {
                    Column {
                        Thumbnail(photo.path)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (photo.kind == "menu") "メニュー表" else "外観・料理",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    val path = repository.removePhoto(photo.id)
                                    if (path != null) {
                                        withContext(Dispatchers.IO) { PhotoStore.delete(path) }
                                    }
                                }
                            }) { Text("削除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(path: String) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { PhotoStore.thumbnail(path) }
    }
    val current = bitmap
    if (current == null) {
        Text("読み込み中", modifier = Modifier.padding(16.dp))
    } else {
        androidx.compose.foundation.Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
    }
}
