package io.github.douglasadvaita.geoalarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
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
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.douglasadvaita.geoalarm.service.WatchService
import io.github.douglasadvaita.geoalarm.ui.theme.GeoAlarmTheme
import io.github.douglasadvaita.geoalarm.util.BusAlertPrefs
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var audioMgr: AudioManager? = null
    private var origVol = -1
    private var active = true
    private val ttsHandler = Handler(Looper.getMainLooper())
    private var flashJob: Job? = null
    private val flashScope = CoroutineScope(Dispatchers.IO)

    private val ttsRepeat = object : Runnable {
        override fun run() {
            if (!active) return
            speakOnce()
            ttsHandler.postDelayed(this, 6000)
        }
    }

    private var speakText = "Tu parada. Despierta."

    private fun speakOnce() {
        try { tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "bus") } catch (_: Exception) {}
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
        val message = intent.getStringExtra("message") ?: ""
        val dist = intent.getFloatExtra("dist", 0f)
        val cfg = BusAlertPrefs.load(this)
        speakText = "Tu parada $name. Despierta."

        boostVolume(cfg.boostVolume)
        startSound(cfg)
        startVib(cfg.strongVib)
        if (cfg.tts) startTts()
        if (cfg.flash) startFlash()

        setContent {
            GeoAlarmTheme {
                val ctx = LocalContext.current
                var tapsLeft by remember { mutableIntStateOf(if (cfg.tripleConfirm) 3 else 1) }
                DisposableEffect(Unit) { onDispose { } }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("¡TU PARADA!", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    if (message.isNotBlank()) Text(message)
                    Text("a ${dist.toInt()} m de tu destino")
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (tapsLeft > 1) {
                            tapsLeft--
                        } else {
                            shutdown()
                            (ctx as? Activity)?.finish()
                        }
                    }) {
                        Text(if (cfg.tripleConfirm) "ESTOY DESPIERTO (${tapsLeft})" else "ESTOY DESPIERTO")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        shutdown()
                        val aid = intent.getLongExtra("alarmId", 0L)
                        ctx.startService(Intent(ctx, WatchService::class.java).apply {
                            action = WatchService.ACTION_SNOOZE_5
                            putExtra("alarmId", aid)
                        })
                        (ctx as? Activity)?.finish()
                    }) {
                        Text("Posponer 5 min")
                    }
                }
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
                        ttsHandler.postDelayed(ttsRepeat, 6000)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun startFlash() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull() ?: return
            flashJob = flashScope.launch {
                while (active) {
                    try { cm.setTorchMode(id, true) } catch (_: Exception) { break }
                    delay(500)
                    try { cm.setTorchMode(id, false) } catch (_: Exception) { break }
                    delay(500)
                }
                try { cm.setTorchMode(id, false) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun shutdown() {
        active = false
        try { ttsHandler.removeCallbacks(ttsRepeat) } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { flashJob?.cancel() } catch (_: Exception) {}
        flashJob = null
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
