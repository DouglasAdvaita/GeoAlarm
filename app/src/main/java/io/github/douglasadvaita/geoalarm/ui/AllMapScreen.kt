package io.github.douglasadvaita.geoalarm.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllMapScreen(vm: AlarmViewModel, onBack: () -> Unit) {
    val alarms by vm.alarms.collectAsState()
    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Todos los destinos") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.headlineMedium) } }
            )
        }
    ) { pad ->
        val ctx = LocalContext.current
        var myLat by remember { mutableStateOf<Double?>(null) }
        var myLng by remember { mutableStateOf<Double?>(null) }
        var mapView by remember { mutableStateOf<MapView?>(null) }
        LaunchedEffect(ctx) {
            val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                try {
                    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                        .addOnSuccessListener { loc: Location? ->
                            if (loc != null) { myLat = loc.latitude; myLng = loc.longitude }
                        }
                } catch (_: SecurityException) {}
            }
        }
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text(
                "${alarms.size} puntos. Toca un marcador para ver el nombre.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).clipToBounds()) {
            AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            factory = { c: Context ->
                org.osmdroid.views.MapView(c).apply {
                    mapView = this
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
                if (myLat != null && myLng != null) {
                    val me = GeoPoint(myLat!!, myLng!!)
                    mv.overlays.add(Marker(mv).apply {
                        position = me
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Yo"
                        // Instancia propia del ícono: no tocar el compartido o se tiñen todos los pines
                        icon = ContextCompat.getDrawable(mv.context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply { setTint(0xFFE53935.toInt()) } ?: icon
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
                FloatingActionButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            try {
                                LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                                    .addOnSuccessListener { loc: Location? ->
                                        if (loc != null) {
                                            myLat = loc.latitude; myLng = loc.longitude
                                            mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                            mapView?.controller?.setZoom(17.0)
                                        }
                                    }
                            } catch (_: SecurityException) {}
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                ) { Text("📍") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
