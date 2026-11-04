package io.github.douglasadvaita.geoalarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.douglasadvaita.geoalarm.service.LiveFix
import io.github.douglasadvaita.geoalarm.service.WatchService
import io.github.douglasadvaita.geoalarm.service.WatchState
import io.github.douglasadvaita.geoalarm.ui.theme.GeoAlarmTheme
import io.github.douglasadvaita.geoalarm.util.BusAlertPrefs
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// Distancia, velocidad y ETA calculados en la pantalla de alarma
private data class TrackInfo(
    val distM: Float,
    val speedMps: Float,
    val etaSec: Int?,
    val trend: Int,
    val atMs: Long,
    val accuracyM: Float
)

class AlarmRingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var audioMgr: AudioManager? = null
    private var origVol = -1
    private var active = true
    private val ttsHandler = Handler(Looper.getMainLooper())
    private var destName = "Destino"
    @Volatile private var info: TrackInfo? = null

    private val ttsRepeat = object : Runnable {
        override fun run() {
            if (!active) return
            speakOnce()
            ttsHandler.postDelayed(this, 8000)
        }
    }

    private fun speakOnce() {
        try { tts?.speak(voiceText(), TextToSpeech.QUEUE_FLUSH, null, "bus") } catch (_: Exception) {}
    }

    private fun speakNow(text: String) {
        if (!active || tts == null) return
        try { tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "bus-now") } catch (_: Exception) {}
    }

    // Arma la frase con lo que se sabe en ese momento: distancia y ETA reales
    private fun voiceText(): String {
        val i = info
        val fresh = i != null && System.currentTimeMillis() - i.atMs < 30_000L
        if (i == null || !fresh) return "Tu parada $destName. Despierta."
        val where = if (i.distM >= 1000f) {
            "Faltan ${fmtDist(i.distM).replace(" km", " kilómetros")}"
        } else {
            "A ${i.distM.roundToInt()} metros"
        }
        val eta = i.etaSec?.let { s -> if (s >= 30) ", ${speechEta(s)}" else "" } ?: ""
        return "Tu parada $destName. $where$eta."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val name = intent.getStringExtra("name") ?: "Destino"
        destName = name
        val message = intent.getStringExtra("message") ?: ""
        val dist = intent.getFloatExtra("dist", 0f)
        val destLat = intent.getDoubleExtra("lat", 0.0)
        val destLng = intent.getDoubleExtra("lng", 0.0)
        val destRadius = intent.getIntExtra("radius", 500)
        val cfg = BusAlertPrefs.load(this)

        boostVolume(cfg.boostVolume)
        startSound(cfg)
        startVib(cfg.strongVib)
        if (cfg.tts) startTts()

        setContent {
            GeoAlarmTheme {
                val ctx = LocalContext.current
                var silenced by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Text("¡TU PARADA!", style = MaterialTheme.typography.headlineLarge)
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    if (message.isNotBlank()) Text(message)
                    Spacer(Modifier.height(4.dp))
                    if (destLat != 0.0 || destLng != 0.0) {
                        TrackPanel(
                            destLat = destLat, destLng = destLng, destName = name,
                            radius = destRadius, startDist = dist, silenced = silenced,
                            proximity = cfg.proximity,
                            onInfo = { info = it },
                            onAnnounce = { txt -> speakNow(txt) }
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("a ${dist.toInt()} m de tu destino")
                    }
                    Button(onClick = {
                        if (!silenced) {
                            // Silencia sonido/vibración/voz, pero deja visible el avance hasta cerrar con X
                            silenceOnly()
                            silenced = true
                        }
                    }) {
                        Text(if (!silenced) "Silenciar" else "Silenciado")
                    }
                    } // Column
                    androidx.compose.material3.IconButton(
                        onClick = {
                            shutdown()
                            (ctx as? Activity)?.finish()
                        },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).semantics { contentDescription = "Cerrar" }
                    ) {
                        Text("✕", style = MaterialTheme.typography.headlineSmall)
                    }
                } // Box
            }
        }
    }

    private fun boostVolume(enable: Boolean) {
        if (!enable) return
        try {
            audioMgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            origVol = audioMgr?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            val max = audioMgr?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: return
            audioMgr?.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (_: Exception) {}
    }

    private fun startSound(cfg: BusAlertPrefs.Cfg) {
        try {
            val uri: Uri = cfg.ringtoneUri?.let { Uri.parse(it) }
                ?: BusAlertPrefs.defaultAlarmUri(this)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmRingActivity, uri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun startVib(strong: Boolean) {
        try {
            val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val pat = if (strong) longArrayOf(0, 1000, 400, 1000, 400, 2000) else longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pat, 0))
            } else {
                @Suppress("DEPRECATION") vib.vibrate(pat, 0)
            }
        } catch (_: Exception) {}
    }

    private fun startTts() {
        try {
            tts = TextToSpeech(this) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale("es", "UY")
                        speakOnce()
                        ttsHandler.postDelayed(ttsRepeat, 8000)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun silenceOnly() {
        active = false
        try { ttsHandler.removeCallbacks(ttsRepeat) } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) {}
        try {
            if (origVol >= 0) audioMgr?.setStreamVolume(AudioManager.STREAM_ALARM, origVol, 0)
        } catch (_: Exception) {}
    }

    private fun shutdown() {
        active = false
        try { ttsHandler.removeCallbacks(ttsRepeat) } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) {}
        try {
            if (origVol >= 0) audioMgr?.setStreamVolume(AudioManager.STREAM_ALARM, origVol, 0)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}

@Composable
private fun TrackPanel(
    destLat: Double,
    destLng: Double,
    destName: String,
    radius: Int,
    startDist: Float,
    silenced: Boolean,
    proximity: Boolean,
    onInfo: (TrackInfo) -> Unit,
    onAnnounce: (String) -> Unit
) {
    val ctx = LocalContext.current
    // Posición del servicio (cada 5 s) y, por si no está corriendo, updates propios
    val svcFix = WatchState.live.collectAsState().value
    var ownFix by remember { mutableStateOf<LiveFix?>(null) }

    DisposableEffect(Unit) {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                ownFix = LiveFix(
                    lat = l.latitude,
                    lng = l.longitude,
                    speedMps = if (l.hasSpeed()) l.speed else 0f,
                    accuracyM = if (l.hasAccuracy()) l.accuracy else 0f,
                    bearing = if (l.hasBearing()) l.bearing else 0f,
                    atMs = l.time
                )
            }
        }
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateDistanceMeters(3f)
                .build()
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {}
        onDispose {
            try { client.removeLocationUpdates(cb) } catch (_: Exception) {}
        }
    }

    val own = ownFix
    val svc = svcFix
    val best = when {
        svc == null -> own
        own == null -> svc
        own.atMs >= svc.atMs -> own
        else -> svc
    }

    var track by remember { mutableStateOf<TrackInfo?>(null) }
    LaunchedEffect(best?.atMs) {
        val f = best ?: return@LaunchedEffect
        val res = FloatArray(1)
        Location.distanceBetween(f.lat, f.lng, destLat, destLng, res)
        val dist = res[0]
        val prev = track
        var raw = f.speedMps
        if (raw < 0.5f && prev != null) {
            val dt = (f.atMs - prev.atMs).coerceAtLeast(1L) / 1000f
            if (dt >= 0.5f) raw = ((prev.distM - dist) / dt).coerceAtLeast(0f)
        }
        val smooth = if (prev != null) prev.speedMps * 0.6f + raw * 0.4f else raw
        val eta = if (smooth >= 0.8f) (dist / smooth).roundToInt() else null
        val trend = prev?.let {
            if (dist < it.distM - 3f) 1 else if (dist > it.distM + 3f) -1 else 0
        } ?: 0
        val t = TrackInfo(dist, smooth, eta, trend, f.atMs, f.accuracyM)
        track = t
        onInfo(t)
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val t = track
    val ageMs = t?.let { nowMs - it.atMs } ?: 0L
    val fresh = t != null && ageMs < 30_000L
    val remaining = t?.etaSec?.let { eta ->
        if (fresh) max(0, eta - (ageMs / 1000).toInt()) else null
    }

    // Re-aviso de voz al cruzar umbrales de cercanía (una vez por umbral)
    val announced = remember { mutableSetOf<Int>() }
    if (proximity) {
        val thresholds = remember(radius) {
            listOf(radius * 0.6f, radius * 0.3f, 50f)
                .map { it.roundToInt().coerceAtLeast(20) }
                .distinct()
                .sortedDescending()
        }
        LaunchedEffect(t?.distM?.roundToInt()?.div(10), silenced, proximity) {
            val d = t?.distM ?: return@LaunchedEffect
            if (silenced) return@LaunchedEffect
            for (th in thresholds) {
                if (d <= th && announced.add(th)) {
                    onAnnounce("Atención: quedan $th metros para $destName")
                } else if (d > th + 40) {
                    announced.remove(th)
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                t == null -> "Buscando GPS…"
                !fresh -> "Sin GPS (última señal hace ${(ageMs / 1000).toInt()} s)"
                else -> "a ${fmtDist(t.distM)} de tu destino"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        if (fresh && t != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val etaTxt = when {
                    remaining == null -> "sin velocidad"
                    remaining <= 0 -> "llegando"
                    else -> "≈ ${fmtEta(remaining)}"
                }
                val trendTxt = when (t.trend) {
                    1 -> " · acercándote"
                    -1 -> " · alejándote"
                    else -> ""
                }
                Text(
                    "$etaTxt  ·  ${fmtSpeed(t.speedMps)}$trendTxt",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            if (t.accuracyM > 60f) {
                Text(
                    "GPS impreciso (±${t.accuracyM.roundToInt()} m)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (startDist > 0f) {
                val prog = (1f - t.distM / startDist).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { prog },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LiveMap(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            destLat = destLat,
            destLng = destLng,
            radius = radius,
            myLat = best?.lat,
            myLng = best?.lng,
            accuracyM = t?.accuracyM ?: 0f,
            distM = t?.distM ?: 0f
        )
    }
}

@Composable
private fun LiveMap(
    modifier: Modifier,
    destLat: Double,
    destLng: Double,
    radius: Int,
    myLat: Double?,
    myLng: Double?,
    accuracyM: Float,
    distM: Float
) {
    var follow by remember { mutableStateOf(true) }
    var fitted by remember { mutableStateOf(false) }
    Box(modifier.clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            factory = { c: Context ->
                org.osmdroid.views.MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setTilesScaledToDpi(true)
                    onResume()
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(destLat, destLng))
                    // Si el usuario mueve el mapa, se apaga el seguimiento
                    setOnTouchListener { _, e ->
                        if (e.action == MotionEvent.ACTION_DOWN) follow = false
                        false
                    }
                }
            },
            update = { mv ->
                mv.overlays.clear()
                val dest = GeoPoint(destLat, destLng)
                mv.overlays.add(Polygon().apply {
                    points = Polygon.pointsAsCircle(dest, radius.toDouble())
                        .filterIndexed { i, _ -> i % 18 == 0 }
                    fillColor = 0x335577FF
                    strokeColor = 0xFF5577FF.toInt()
                    strokeWidth = 3f
                })
                mv.overlays.add(Marker(mv).apply {
                    position = dest
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destino"
                })
                if (myLat != null && myLng != null) {
                    val me = GeoPoint(myLat, myLng)
                    if (accuracyM in 1f..300f) {
                        mv.overlays.add(Polygon().apply {
                            points = Polygon.pointsAsCircle(me, accuracyM.toDouble())
                                .filterIndexed { i, _ -> i % 18 == 0 }
                            fillColor = 0x2233AAFF
                            strokeColor = 0x5533AAFF
                            strokeWidth = 1f
                        })
                    }
                    mv.overlays.add(Polyline().apply {
                        setPoints(listOf(me, dest))
                        outlinePaint.color = 0xCCE53935.toInt()
                        outlinePaint.strokeWidth = 6f
                    })
                    mv.overlays.add(Marker(mv).apply {
                        position = me
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Yo"
                        // Instancia propia del ícono: no tocar el compartido o se tiñen todos los pines
                        icon = ContextCompat.getDrawable(mv.context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply { setTint(0xFFE53935.toInt()) } ?: icon
                    })
                    if (!fitted && distM > 0f) {
                        fitted = true
                        when {
                            distM in 120f..2000f -> mv.post {
                                try {
                                    mv.zoomToBoundingBox(
                                        BoundingBox.fromGeoPoints(listOf(me, dest)), false, 140
                                    )
                                } catch (_: Exception) {}
                            }
                            distM > 2000f -> {
                                // Simulación desde lejos: quedarse en el destino, no abarcar toda la ciudad
                                mv.controller.setZoom(14.0)
                                mv.controller.setCenter(dest)
                            }
                            else -> {
                                mv.controller.setZoom(17.5)
                                mv.controller.setCenter(me)
                            }
                        }
                    } else if (fitted && follow && distM in 1f..2000f) {
                        mv.controller.animateTo(
                            GeoPoint((myLat + destLat) / 2.0, (myLng + destLng) / 2.0)
                        )
                    }
                }
                mv.invalidate()
            },
            onRelease = { mv ->
                try { mv.onPause() } catch (_: Exception) {}
                mv.onDetach()
            }
        )
        if (!follow) {
            TextButton(
                onClick = { follow = true; fitted = false },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) { Text("Centrar") }
        }
    }
}

private fun fmtDist(m: Float): String =
    if (m < 1000f) "${m.roundToInt()} m"
    else String.format(Locale.US, "%.1f km", m / 1000f).replace('.', ',')

private fun fmtEta(sec: Int): String = when {
    sec < 60 -> "$sec s"
    sec < 3600 -> "${sec / 60} min"
    else -> "${sec / 3600} h ${(sec % 3600) / 60} min"
}

private fun fmtSpeed(mps: Float): String =
    if (mps < 0.8f) "detenido" else "${(mps * 3.6f).roundToInt()} km/h"

private fun speechEta(sec: Int): String {
    val min = (sec + 30) / 60
    return if (min <= 1) "falta menos de un minuto" else "faltan $min minutos"
}
