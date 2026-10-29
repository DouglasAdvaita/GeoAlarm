package io.github.douglasadvaita.geoalarm.ui

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.douglasadvaita.geoalarm.data.Alarm
import io.github.douglasadvaita.geoalarm.service.WatchService
import io.github.douglasadvaita.geoalarm.service.WatchState

@Composable
fun ListScreen(vm: AlarmViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, onMap: () -> Unit, onTrips: () -> Unit) {
    val alarms by vm.alarms.collectAsState()
    val isWatching by WatchState.isWatching.collectAsState()
    val enabledCount by WatchState.enabledCount.collectAsState()
    val nearest by WatchState.nearestText.collectAsState()
    val ctx = LocalContext.current

    val enabledTotal = alarms.count { it.enabled }
    // Se asume todo lo habilitado: el conteo es habilitadas, no solo en servicio
    val countShown = if (enabledTotal > 0) enabledTotal else enabledCount

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = onAdd) { Text("+") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            StatusPill(
                isWatching = isWatching,
                count = countShown,
                nearest = nearest,
                onToggle = {
                    if (isWatching) {
                        ctx.startService(Intent(ctx, WatchService::class.java).apply { action = WatchService.ACTION_STOP })
                    } else {
                        ctx.startService(Intent(ctx, WatchService::class.java).apply { action = WatchService.ACTION_START })
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Todo lo habilitado se vigila. Al llegar descansa hasta mañana 06:00.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                GpsOffInline()
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMap) { Text("🗺️ Mapa") }
                TextButton(onClick = onTrips) { Text("Recorridos") }
            }
            Spacer(Modifier.height(4.dp))
            if (alarms.isEmpty()) {
                Text("Sin alarmas. Toca + para crear: ej. 'Parada ómnibus' o 'Super'.")
            }
            LazyColumn {
                items(alarms, key = { it.id }) { a ->
                    AlarmCard(a,
                        onToggle = { vm.toggle(a) },
                        onEditFull = { onEdit(a.id) },
                        onDelete = { vm.delete(a) },
                        onSaveMessage = { vm.updateMessage(a, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(isWatching: Boolean, count: Int, nearest: String, onToggle: () -> Unit) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "blink"
    )
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        color = if (isWatching) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isWatching) "● VIGILANDO $count" else "○ DETENIDO",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = if (isWatching) Modifier.alpha(blink) else Modifier
                )
                if (isWatching && nearest.isNotEmpty()) {
                    Text(nearest, style = MaterialTheme.typography.bodySmall)
                } else if (!isWatching) {
                    Text("Toca Vigilar para activar", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onToggle) { Text(if (isWatching) "Detener" else "Vigilar") }
        }
    }
}

@Composable
private fun GpsOffInline() {
    val ctx = LocalContext.current
    var gpsOn by remember { mutableStateOf(isGpsOn(ctx)) }
    DisposableEffect(ctx) {
        val update = {
            gpsOn = isGpsOn(ctx)
        }
        update()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                update()
            }
        }
        try {
            ctx.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        } catch (_: Exception) {}
        onDispose {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }
    if (gpsOn) return
    val blink by rememberInfiniteTransition(label = "gpsblink").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "gpsblink"
    )
    Text(
        "GPS apagado",
        color = Color.Red,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .alpha(blink)
            .padding(start = 8.dp)
            .clickable {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
    )
}

private fun isGpsOn(ctx: Context): Boolean {
    return try {
        (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: Exception) { true }
}

@Composable
private fun AlarmCard(
    a: Alarm,
    onToggle: () -> Unit,
    onEditFull: () -> Unit,
    onDelete: () -> Unit,
    onSaveMessage: (String) -> Unit
) {
    var editingMsg by remember { mutableStateOf(false) }
    var draft by remember(a.id, a.message) { mutableStateOf(a.message) }
    val ctx = LocalContext.current
    val icon = if (a.type == Alarm.TYPE_BUS) "🚌🔔" else "📝"
    val snoozed = a.snoozedUntil > System.currentTimeMillis()

    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$icon  ${a.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = a.enabled, onCheckedChange = { onToggle() })
            }
            if (snoozed) {
                Text("Descansa hasta mañana 06:00", style = MaterialTheme.typography.bodySmall)
            }
            if (!editingMsg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.message.ifBlank { "Sin nota" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { editingMsg = true }) { Text("✏️") }
                }
            } else {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    label = { Text("Nota") }, modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(onClick = { onSaveMessage(draft); editingMsg = false }) { Text("Guardar") }
                    TextButton(onClick = { draft = a.message; editingMsg = false }) { Text("Cancelar") }
                }
            }
            Row {
                TextButton(onClick = onEditFull) { Text("Editar punto") }
                TextButton(onClick = onDelete) { Text("Borrar") }
            }
            Row {
                TextButton(onClick = {
                    try {
                        val c = ctx as? android.app.Activity ?: return@TextButton
                        val i = android.content.Intent(c, AlarmRingActivity::class.java).apply {
                            putExtra("name", a.name)
                            putExtra("message", a.message)
                            putExtra("dist", 0f)
                            putExtra("alarmId", a.id)
                            putExtra("lat", a.lat)
                            putExtra("lng", a.lng)
                            putExtra("radius", a.radiusMeters)
                        }
                        c.startActivity(i)
                    } catch (_: Exception) {}
                }) { Text("🔔 Probar llegada") }
            }
        }
    }
}
