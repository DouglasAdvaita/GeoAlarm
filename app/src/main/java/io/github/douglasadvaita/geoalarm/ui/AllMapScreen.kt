package io.github.douglasadvaita.geoalarm.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun AllMapScreen(vm: AlarmViewModel, onBack: () -> Unit) {
    val alarms by vm.alarms.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Todos los destinos", style = MaterialTheme.typography.titleLarge)
        Text(
            "${alarms.size} puntos. Toca un marcador para ver el nombre.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { c: Context ->
                org.osmdroid.views.MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setTilesScaledToDpi(true)
                    // Carga de teselas fuera del hilo principal: evita ANR ("esperar o cerrar")
                    setUseDataConnection(true)
                    controller.setZoom(11.0)
                    if (alarms.isNotEmpty()) {
                        controller.setCenter(GeoPoint(alarms[0].lat, alarms[0].lng))
                    } else {
                        controller.setCenter(GeoPoint(-34.9011, -56.1645))
                    }
                    // Ciclo de vida: sin onResume el mapa queda congelado cargando
                    onResume()
                }
            },
            update = { mv ->
                mv.overlays.clear()
                val pts = mutableListOf<GeoPoint>()
                // Círculos livianos (20 lados): pointsAsCircle con 360 bordes x N alarmas trababa el hilo principal
                for (a in alarms) {
                    val p = GeoPoint(a.lat, a.lng)
                    pts.add(p)
                                     mv.overlays.add(Polygon().apply {
                        points = Polygon.pointsAsCircle(p, a.radiusMeters.toDouble())
                            .filterIndexed { i, _ -> i % 18 == 0 }
                        fillColor = 0x335577FF
                        strokeColor = 0xFF5577FF.toInt()
                        strokeWidth = 3f
                    })
                    mv.overlays.add(Marker(mv).apply {
                        position = p
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = a.name
                    })
                }
                mv.invalidate()
                // Zoom diferido: solo cuando el mapa ya tiene tamaño, una sola vez por lista
                if (pts.size > 1) {
                    mv.post {
                        try {
                            val bb = BoundingBox.fromGeoPoints(pts)
                            mv.zoomToBoundingBox(bb, false, 120)
                        } catch (_: Exception) {}
                    }
                }
            },
            onRelease = { mv -> try { mv.onPause() } catch (_: Exception) {}; mv.onDetach() }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Volver") }
    }
}
