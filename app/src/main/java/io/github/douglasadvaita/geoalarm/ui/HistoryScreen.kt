package io.github.douglasadvaita.geoalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.util.TrackDates
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).trackDao() }
    val arrivals by remember { dao.observeAllArrivals() }
        .collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Historial de alarmas", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${arrivals.size} sonada${if (arrivals.size == 1) "" else "s"} registrada${if (arrivals.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Spacer(Modifier.height(8.dp))
        if (arrivals.isEmpty()) {
            Text("Sin alarmas sonadas todavía. Se guarda cada sonada con destino, nota, fecha y hora.")
        } else {
            LazyColumn {
                items(arrivals, key = { it.id }) { arrival ->
                    val day = TrackDates.dateOf(arrival.dayKey)
                    val time = Instant.ofEpochMilli(arrival.arrivedAtMs)
                        .atZone(ZoneId.systemDefault())
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    arrival.destination,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "%02d/%02d · %02d:%02d".format(
                                        Locale.US, day.dayOfMonth, day.monthValue, time.hour, time.minute
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Nota: ${arrival.message.ifBlank { "sin texto" }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
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
}
