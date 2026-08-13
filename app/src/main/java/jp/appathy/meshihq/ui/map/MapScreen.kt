package jp.appathy.meshihq.ui.map

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import jp.appathy.meshihq.Prefs
import jp.appathy.meshihq.data.repo.ShopRepository
import jp.appathy.meshihq.domain.Categories
import jp.appathy.meshihq.ui.LocationUtil
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    repository: ShopRepository,
    onOpenShop: (Long) -> Unit,
    onNewShopAt: (Double, Double) -> Unit,
    onImportHere: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val shopsFlow = remember { repository.observeShops() }
    val shops by shopsFlow.collectAsState(initial = emptyList())

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            val start = LocationUtil.lastKnown(context)
            controller.setCenter(
                if (start != null) GeoPoint(start.latitude, start.longitude)
                else GeoPoint(Prefs.defaultLat(context), Prefs.defaultLon(context))
            )
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地図（長押しで新規登録）") },
                actions = {
                    TextButton(onClick = {
                        val center = mapView.mapCenter
                        onImportHere(center.latitude, center.longitude)
                    }) {
                        Text("この範囲を取込")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val here = LocationUtil.lastKnown(context)
                if (here != null) {
                    mapView.controller.animateTo(GeoPoint(here.latitude, here.longitude))
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "現在地へ")
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { mapView },
            update = { view ->
                view.overlays.clear()
                val events = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { onNewShopAt(it.latitude, it.longitude) }
                        return true
                    }
                })
                view.overlays.add(events)
                shops.forEach { shop ->
                    val marker = Marker(view)
                    marker.position = GeoPoint(shop.lat, shop.lon)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.title = shop.name + "（" + shop.category + "）"
                    marker.icon = pinDrawable(Categories.colorOf(shop.category))
                    marker.setOnMarkerClickListener { _, _ ->
                        onOpenShop(shop.id)
                        true
                    }
                    view.overlays.add(marker)
                }
                view.invalidate()
            }
        )
    }
}

private fun pinDrawable(color: Int): ShapeDrawable {
    val drawable = ShapeDrawable(OvalShape())
    drawable.paint.color = color
    drawable.intrinsicWidth = 40
    drawable.intrinsicHeight = 40
    drawable.setBounds(0, 0, 40, 40)
    return drawable
}
