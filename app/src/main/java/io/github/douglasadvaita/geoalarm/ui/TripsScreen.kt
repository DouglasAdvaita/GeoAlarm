package io.github.douglasadvaita.geoalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.data.TrackDay
import io.github.douglasadvaita.geoalarm.data.TrackStay
import io.github.douglasadvaita.geoalarm.service.WatchService
import io.github.douglasadvaita.geoalarm.util.GpxExport
import io.github.douglasadvaita.geoalarm.util.TrackCodec
import io.github.douglasadvaita.geoalarm.util.TrackDates
import io.github.douglasadvaita.geoalarm.util.TrackPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

@Composable
fun TripsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).trackDao() }
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    var anchor by remember { mutableStateOf(today) }
    var monthMode by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(TrackPrefs.enabled(ctx)) }
    var exporting by remember { mutableStateOf(false) }

    val range = if (monthMode) TrackDates.monthRange(anchor) else TrackDates.weekRange(anchor)
    val fromKey = range.first
    val toKey = range.second
    val days by remember(fromKey, toKey) { dao.observeDays(fromKey, toKey) }
        .collectAsState(initial = emptyList())
    val stays by remember(fromKey, toKey) { dao.observeStays(fromKey, toKey) }
        .collectAsState(initial = emptyList())
    val arrivals by remember(fromKey, toKey) { dao.observeArrivals(fromKey, toKey) }
        .collectAsState(initial = emptyList())

    val label = if (monthMode) {
        TrackDates.monthName(anchor) + " " + anchor.year
    } else {
        val monday = TrackDates.dateOf(fromKey)
        val sunday = TrackDates.dateOf(toKey)
        "${monday.dayOfMonth} ${TrackDates.monthName(monday).take(3)} – " +
            "${sunday.dayOfMonth} ${TrackDates.monthName(sunday).take(3)}"
    }

    val hasLocation = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun shift(dir: Long) {
        anchor = if (monthMode) anchor.plusMonths(dir) else anchor.plusDays(7 * dir)
    }

    fun toggleTracking(on: Boolean) {
        tracking = on
        TrackPrefs.setEnabled(ctx, on)
        val i = Intent(ctx, WatchService::class.java).apply { action = WatchService.ACTION_REFRESH }
        try {
            if (on) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        } catch (_: Exception) {}
    }

    fun export() {
        exporting = true
        scope.launch {
            val a = dao.getDays(fromKey, toKey)
            val b = dao.getStays(fromKey, toKey)
            if (a.isEmpty()) {
                exporting = false
                Toast.makeText(ctx, "No hay recorridos en el período", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val gpx = GpxExport.build(a, b)
            val file = withContext(Dispatchers.IO) {
                GpxExport.write(ctx, "recorridos_${fromKey}_$toKey.gpx", gpx)
            }
            exporting = false
            if (file != null) GpxExport.share(ctx, file)
            else Toast.makeText(ctx, "No se pudo generar el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recorridos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = tracking, onCheckedChange = { toggleTracking(it) })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Seguimiento continuo", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Guarda recorridos y permanencias de cada día. Solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (tracking && !hasLocation) {
            Text(
                "Falta permiso de ubicación: no se podrá registrar.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { shift(-1) }) { Text("‹") }
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { shift(1) }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthMode = !monthMode; anchor = today }) {
                Text(if (monthMode) "Ver semana" else "Ver mes")
            }
            TextButton(onClick = { anchor = today }) { Text("Hoy") }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !exporting, onClick = { export() }) {
                Text(if (exporting) "Exportando…" else "Exportar GPX")
            }
        }
        TripsMap(
            modifier = Modifier.fillMaxWidth().weight(1f),
            days = days,
            stays = stays,
            rangeKey = "$fromKey-$toKey"
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(days, key = { it.dayKey }) { d -> LegendChip(d) }
        }
        Spacer(Modifier.height(4.dp))
        val kb = days.sumOf { it.encoded.length } / 1024.0
        Text(
            "${days.size} días · ${stays.size} permanencias · ${arrivals.size} sonadas · ≈ " +
                String.format(Locale.US, "%.1f", kb).replace('.', ',') + " KB",
            style = MaterialTheme.typography.bodySmall
        )
        if (arrivals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Alarmas sonadas", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(arrivals, key = { it.id }) { arrival ->
                    val time = java.time.Instant.ofEpochMilli(arrival.arrivedAtMs)
                        .atZone(java.time.ZoneId.systemDefault())
                    Column(Modifier.width(190.dp)) {
                        Text(arrival.destination, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%02d:%02d · ${arrival.message.ifBlank { "sin texto" }}".format(time.hour, time.minute),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${"%.5f".format(Locale.US, arrival.lat)}, ${"%.5f".format(Locale.US, arrival.lng)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(d: TrackDay) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp)
                .background(Color(dayColor(d.dayKey)), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "${TrackDates.weekdayShort(d.dayKey)} ${TrackDates.dateOf(d.dayKey).dayOfMonth} · " +
                String.format(Locale.US, "%.1f", d.distanceM / 1000.0).replace('.', ',') + " km",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TripsMap(modifier: Modifier, days: List<TrackDay>, stays: List<TrackStay>, rangeKey: String) {
    val fitRef = remember { arrayOf("") }
    AndroidView(
        modifier = modifier,
        factory = { c: Context ->
            org.osmdroid.views.MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setTilesScaledToDpi(true)
                onResume()
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(-34.9011, -56.1645))
            }
        },
        update = { mv ->
            mv.overlays.clear()
            val all = ArrayList<GeoPoint>()
            for (d in days) {
                val pts = TrackCodec.decode(d.startLat, d.startLng, d.startMs, d.encoded)
                if (pts.size >= 2) {
                    mv.overlays.add(Polyline().apply {
                        setPoints(pts.map { GeoPoint(it.lat, it.lng) })
                        outlinePaint.color = dayColor(d.dayKey)
                        outlinePaint.strokeWidth = 8f
                    })
                    all.add(GeoPoint(pts.first().lat, pts.first().lng))
                    all.add(GeoPoint(pts.last().lat, pts.last().lng))
                }
            }
            for (s in stays) {
                val color = dayColor(s.dayKey)
                val rgb = color and 0x00FFFFFF
                val c = GeoPoint(s.lat, s.lng)
                all.add(c)
                mv.overlays.add(Polygon().apply {
                    points = Polygon.pointsAsCircle(c, s.radiusM.toDouble())
                        .filterIndexed { i, _ -> i % 18 == 0 }
                    fillColor = rgb or 0x33000000
                    strokeColor = rgb or (0x99000000.toInt())
                    strokeWidth = 2f
                })
                mv.overlays.add(Marker(mv).apply {
                    position = c
                    title = "Permanencia"
                    snippet = "${s.radiusM.toInt()} m de radio"
                })
            }
            mv.invalidate()
            val fitKey = "$rangeKey-${days.size}-${stays.size}-${days.firstOrNull()?.endMs ?: 0}"
            if (all.isNotEmpty() && fitRef[0] != fitKey) {
                fitRef[0] = fitKey
                mv.post {
                    try {
                        if (all.size > 1) {
                            mv.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), false, 100)
                        } else {
                            mv.controller.setZoom(16.0)
                            mv.controller.setCenter(all[0])
                        }
                    } catch (_: Exception) {}
                }
            }
        },
        onRelease = { mv ->
            try { mv.onPause() } catch (_: Exception) {}
            mv.onDetach()
        }
    )
}

// Lunes celeste, martes rojo, miércoles amarillo, jueves azul,
// viernes rosa, sábado gris, domingo naranja
private fun dayColor(dayKey: Int): Int = when (TrackDates.weekdayOf(dayKey)) {
    DayOfWeek.MONDAY -> 0xFF4FC3F7.toInt()
    DayOfWeek.TUESDAY -> 0xFFE53935.toInt()
    DayOfWeek.WEDNESDAY -> 0xFFFDD835.toInt()
    DayOfWeek.THURSDAY -> 0xFF1E88E5.toInt()
    DayOfWeek.FRIDAY -> 0xFFF06292.toInt()
    DayOfWeek.SATURDAY -> 0xFF9E9E9E.toInt()
    DayOfWeek.SUNDAY -> 0xFFFB8C00.toInt()
}
