package io.github.douglasadvaita.geoalarm.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.util.TrackDates
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).trackDao() }
    val arrivals by remember { dao.observeAllArrivals() }
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    fun export() {
        exporting = true
        scope.launch {
            val sb = StringBuilder()
            sb.append("id,dayKey,destino,nota,lat,lng,arrivedAtMs\n")
            for (a in arrivals) {
                sb.append(a.id).append(',')
                sb.append(a.dayKey).append(',')
                sb.append('"').append(a.destination.replace("\"", "\"\"")).append('"').append(',')
                sb.append('"').append(a.message.replace("\"", "\"\"")).append('"').append(',')
                sb.append(a.lat).append(',')
                sb.append(a.lng).append(',')
                sb.append(a.arrivedAtMs).append('\n')
            }
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                File(dir, "historial_alarmas.csv").apply { writeText(sb.toString()) }
            }
            exporting = false
            try {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(i, "Exportar historial"))
            } catch (_: Exception) {
                Toast.makeText(ctx, "No se pudo compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Historial de alarmas") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.headlineMedium) } },
                actions = {
                    TextButton(enabled = !exporting, onClick = { export() }) {
                        Text(if (exporting) "Exportando…" else "Exportar")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text(
                "${arrivals.size} sonada${if (arrivals.size == 1) "" else "s"} registrada${if (arrivals.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall
            )
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
}
