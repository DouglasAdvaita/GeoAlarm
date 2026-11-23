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
import io.github.douglasadvaita.geoalarm.data.TrackArrival
import io.github.douglasadvaita.geoalarm.data.TrotSession
import io.github.douglasadvaita.geoalarm.util.TrotFormat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Una fila común para alarmas y trotes: se ordenan juntos por fecha
private data class HistoryRow(
    val key: String,
    val atMs: Long,
    val title: String,
    val detail: String,
    val coords: String,
    val arrival: TrackArrival? = null,
    val trot: TrotSession? = null
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).trackDao() }
    val arrivals by remember { dao.observeAllArrivals() }
        .collectAsState(initial = emptyList())
    val trots by remember { dao.observeAllTrots() }
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    val rows = remember(arrivals, trots) {
        buildList {
            arrivals.forEach { a ->
                add(
                    HistoryRow(
                        key = "alarma-${a.id}",
                        atMs = a.arrivedAtMs,
                        title = a.destination,
                        detail = "Nota: ${a.message.ifBlank { "sin texto" }}",
                        coords = "${"%.5f".format(Locale.US, a.lat)}, ${"%.5f".format(Locale.US, a.lng)}",
                        arrival = a
                    )
                )
            }
            trots.forEach { t ->
                add(
                    HistoryRow(
                        key = "trote-${t.id}",
                        atMs = t.startMs,
                        title = "🏃 ${t.startPlace.ifBlank { "Trote" }}",
                        detail = TrotFormat.summary(t.distanceM, t.durationMs),
                        coords = "${"%.5f".format(Locale.US, t.startLat)}, ${"%.5f".format(Locale.US, t.startLng)}",
                        trot = t
                    )
                )
            }
        }.sortedByDescending { it.atMs }
    }

    fun export() {
        exporting = true
        scope.launch {
            val fmtDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val fmtTime = DateTimeFormatter.ofPattern("HH:mm:ss")
            fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            fun dateTime(ms: Long): Pair<String, String> =
                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let {
                    it.format(fmtDate) to it.format(fmtTime)
                }

            val sb = StringBuilder()
            // "tipo" permite filtrar en Excel: Alarma / Trote
            sb.append("tipo,fecha,hora,descripcion,tiempo_s,distancia_m,velocidad_kmh,lat,lng,id\n")
            for (r in rows) {
                val (date, time) = dateTime(r.atMs)
                val a = r.arrival
                val t = r.trot
                sb.append(csv(if (a != null) "Alarma" else "Trote")).append(',')
                sb.append(date).append(',').append(time).append(',')
                when {
                    a != null -> {
                        sb.append(csv("${a.destination} | ${a.message}")).append(',')
                        sb.append(',').append(',').append(',').append(',')
                        sb.append(a.lat).append(',').append(a.lng).append(',').append(a.id).append('\n')
                    }
                    t != null -> {
                        val kmh = if (t.durationMs > 0L)
                            t.distanceM / 1000.0 / (t.durationMs / 3_600_000.0) else 0.0
                        sb.append(csv(t.startPlace)).append(',')
                        sb.append(String.format(Locale.US, "%.1f", t.durationMs / 1000.0)).append(',')
                        sb.append(String.format(Locale.US, "%.1f", t.distanceM)).append(',')
                        sb.append(String.format(Locale.US, "%.2f", kmh)).append(',')
                        sb.append(t.startLat).append(',').append(t.startLng).append(',').append(t.id).append('\n')
                    }
                }
            }
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                File(dir, "historial.csv").apply {
                    writeText("\uFEFF" + sb.toString(), Charsets.UTF_8)
                }
            }
            exporting = false
            try {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "historial.csv")
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
                title = { Text("Historial") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                },
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
                "${arrivals.size} alarma${if (arrivals.size == 1) "" else "s"} · " +
                    "${trots.size} trote${if (trots.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) {
                Text("Sin registros todavía. Las alarmas sonadas y los trotes detenidos se guardan acá.")
            } else {
                LazyColumn {
                    items(rows, key = { it.key }) { row ->
                        val time = Instant.ofEpochMilli(row.atMs).atZone(ZoneId.systemDefault())
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        row.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "%02d/%02d · %02d:%02d".format(
                                            Locale.US, time.dayOfMonth, time.monthValue,
                                            time.hour, time.minute
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(row.detail, style = MaterialTheme.typography.bodySmall)
                                Text(row.coords, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
