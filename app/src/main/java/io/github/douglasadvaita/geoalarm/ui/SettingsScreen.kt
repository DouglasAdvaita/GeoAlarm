package io.github.douglasadvaita.geoalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.douglasadvaita.geoalarm.util.ReleaseInfo
import io.github.douglasadvaita.geoalarm.util.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf<ReleaseInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    val curCode = remember { UpdateChecker.currentCode(ctx) }
    val curName = remember { UpdateChecker.currentName(ctx) }

    Scaffold(topBar = { androidx.compose.material3.TopAppBar(title = { Text("Ajustes") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Versión instalada: $curName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; status = "Consultando GitHub…"; latest = null; progress = -1
                    scope.launch {
                        try {
                            val r = UpdateChecker.checkLatest()
                            latest = r
                            status = if (r.versionCode > curCode) "Hay actualización: ${r.tag}"
                            else "Ya estás al día (${r.tag})."
                        } catch (e: Exception) {
                            status = "Error: ${e.message}"
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buscar actualización") }
            Spacer(Modifier.height(8.dp))
            if (status.isNotEmpty()) Text(status)
            latest?.let { r ->
                if (r.versionCode > curCode) {
                    Spacer(Modifier.height(8.dp))
                    if (r.notes.isNotBlank()) Text(r.notes.take(500), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true; progress = 0; status = "Descargando APK…"
                            scope.launch {
                                try {
                                    val apk = UpdateChecker.downloadApk(ctx, r.apkUrl) { progress = it }
                                    status = "Descargado. Abriendo instalador…"
                                    UpdateChecker.installApk(ctx, apk)
                                } catch (e: Exception) {
                                    status = "Error: ${e.message}"
                                } finally { busy = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Descargar e instalar ${r.tag}") }
                    if (progress >= 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}
