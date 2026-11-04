package io.github.douglasadvaita.geoalarm.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.douglasadvaita.geoalarm.MainActivity
import io.github.douglasadvaita.geoalarm.R
import io.github.douglasadvaita.geoalarm.data.Alarm
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.data.TrackArrival
import io.github.douglasadvaita.geoalarm.ui.AlarmRingActivity
import io.github.douglasadvaita.geoalarm.util.Notifications
import io.github.douglasadvaita.geoalarm.util.TrackPrefs
import io.github.douglasadvaita.geoalarm.receiver.TrackCompactionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

class WatchService : Service() {

    companion object {
        const val ACTION_START = "io.github.douglasadvaita.geoalarm.START"
        const val ACTION_STOP = "io.github.douglasadvaita.geoalarm.STOP"
        const val ACTION_REFRESH = "io.github.douglasadvaita.geoalarm.REFRESH"
        const val NOTIF_ID = 1001
        const val GPS_OFF_NOTIF_ID = 1002

        fun nextMorning6am(): Long {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching: List<Alarm> = emptyList()
    private val wasOutside = mutableMapOf<Long, Boolean>()
    // Movimiento real detectado mientras estaba fuera: evita disparar por estar parado en el destino
    private val hasMoved = mutableMapOf<Long, Boolean>()
    private var gpsOffNotified = false
    private var providersReceiver: BroadcastReceiver? = null

    // Seguimiento continuo: comparte el GPS con las alarmas
    private var tracking = false
    private var recorder: TrackRecorder? = null
    private var trackBand = 1
    private var lastBandMs = 0L

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            WatchState.updateLive(
                LiveFix(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
                    bearing = if (loc.hasBearing()) loc.bearing else 0f,
                    atMs = loc.time
                )
            )
            if (tracking) {
                recorder?.onFix(loc)
                val band = when {
                    !loc.hasSpeed() || loc.speed < 1.5f -> 0
                    loc.speed < 8f -> 1
                    loc.speed < 15f -> 2
                    else -> 3
                }
                val now = System.currentTimeMillis()
                if (band != trackBand && now - lastBandMs > 30_000L) {
                    trackBand = band
                    lastBandMs = now
                    applyLocationRequest()
                }
            }
            if (gpsOffNotified && isGpsOn()) refreshGpsState()
            checkTriggers(loc)
            updateNotification(loc)
        }
    }

    private fun isGpsOn(): Boolean {
        return try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { true }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        TrackCompactionReceiver.schedule(this)
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) refreshGpsState()
            }
        }
        providersReceiver = r
        try {
            registerReceiver(r, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatching()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                startWatching()
                return START_STICKY
            }
            else -> startWatching()
        }
        return START_STICKY
    }

    private fun startWatching() {
        tracking = TrackPrefs.enabled(this)
        if (tracking) {
            if (recorder == null) recorder = TrackRecorder(this)
        } else {
            recorder?.stop()
            recorder = null
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, WatchService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, Notifications.CH_WATCH)
            .setContentTitle("GeoAlarm vigilando")
            .setContentText("Obteniendo ubicación…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(0, "Detener", stopPi)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notif,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            WatchState.update(false, 0)
            stopSelf()
            return
        }

        scope.launch {
            // Al abrir / arrancar: reactiva las que ya vencieron su descanso
            val dao = AppDatabase.get(this@WatchService).alarmDao()
            val all = dao.getEnabled()
            val now = System.currentTimeMillis()
            for (a in all) {
                if (a.snoozedUntil > 0 && a.snoozedUntil <= now) {
                    dao.update(a.copy(snoozedUntil = 0L))
                }
            }
            watching = dao.getEnabled()
            WatchState.update(watching.isNotEmpty() || tracking, watching.size)
            if (watching.isEmpty() && !tracking) {
                stopWatching()
                return@launch
            }
            refreshGpsState()
            applyLocationRequest()
        }
    }

    // Intervalo según alarmas activas y velocidad (más seguido cuanto más rápido)
    private fun applyLocationRequest() {
        val hasBus = watching.any { it.type == Alarm.TYPE_BUS }
        val interval = when {
            hasBus -> 5_000L
            !tracking -> 30_000L
            trackBand == 0 -> 20_000L
            trackBand == 2 -> 4_000L
            trackBand == 3 -> 2_000L
            else -> 8_000L
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateDistanceMeters(if (tracking) 5f else 10f)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopWatching()
        }
    }

    private fun checkTriggers(loc: Location) {
        scope.launch {
            val now = System.currentTimeMillis()
            val dao = AppDatabase.get(this@WatchService).alarmDao()
            for (a in watching.toList()) {
                // Descanso hasta mañana: no dispara aunque siga habilitada
                if (a.snoozedUntil > now) continue
                val res = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, a.lat, a.lng, res)
                val dist = res[0]
                val inside = dist <= a.radiusMeters
                val outside = dist > a.radiusMeters * 1.2f
                val moving = loc.hasSpeed() && loc.speed >= 1.5f
                // Primera ubicación conocida
                if (wasOutside[a.id] == null) {
                    if (outside) {
                        wasOutside[a.id] = true
                        hasMoved[a.id] = moving
                    } else {
                        // Ya está dentro al arrancar: no es un viaje, esperar salida + reentrada con movimiento
                        wasOutside[a.id] = false
                        hasMoved[a.id] = false
                    }
                    continue
                }
                if (outside) {
                    wasOutside[a.id] = true
                    if (moving) hasMoved[a.id] = true
                    continue
                }
                // Dentro: exige haber estado fuera antes y haberse movido (viaje real)
                if (wasOutside[a.id] != true) {
                    // Si venía de snooze vencido y sigue dentro, espera a salir
                    continue
                }
                if (hasMoved[a.id] != true) {
                    // Sin movimiento real previo: no disparar (estaba parado en el destino)
                    continue
                }
                fire(a, dist, loc)
                // No se deshabilita: pasa a descansar hasta mañana 06:00
                val updated = a.copy(snoozedUntil = nextMorning6am())
                dao.update(updated)
                watching = watching.map { if (it.id == a.id) updated else it }
                wasOutside[a.id] = false
                hasMoved[a.id] = false
                WatchState.update(true, watching.size)
                break
            }
        }
    }

    private fun fire(a: Alarm, distM: Float, loc: Location) {
        scope.launch {
            try {
                AppDatabase.get(this@WatchService).trackDao().insertArrival(
                    TrackArrival(
                        dayKey = io.github.douglasadvaita.geoalarm.util.TrackDates.dayKey(loc.time),
                        alarmId = a.id,
                        destination = a.name,
                        message = a.message,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        arrivedAtMs = loc.time
                    )
                )
            } catch (_: Exception) {}
        }
        if (a.type == Alarm.TYPE_BUS) {
            // Full-screen intent: el sistema abre aunque esté bloqueado (Android 10+)
            val full = Intent(this, AlarmRingActivity::class.java).apply {
                putExtra("name", a.name)
                putExtra("message", a.message)
                putExtra("dist", distM)
                putExtra("alarmId", a.id)
                putExtra("lat", a.lat)
                putExtra("lng", a.lng)
                putExtra("radius", a.radiusMeters)
            }
            val fullPi = PendingIntent.getActivity(this, a.id.toInt(), full,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(this, Notifications.CH_ALARM)
                .setContentTitle("¡TU PARADA! ${a.name}")
                .setContentText(a.message.ifBlank { "A ${distM.toInt()} m de tu destino" })
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(fullPi, true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(a.id.toInt() + 1000, n)
        } else {
            val open = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(this, Notifications.CH_REMINDER)
                .setContentTitle("📝 ${a.name}")
                .setContentText(a.message.ifBlank { "Estás en el lugar (${distM.toInt()} m)" })
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(a.id.toInt() + 2000, n)
        }
    }

    private fun updateNotification(loc: Location) {
        var nearest: Alarm? = null
        var nearestDist = Float.MAX_VALUE
        for (a in watching) {
            val res = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, a.lat, a.lng, res)
            if (res[0] < nearestDist) {
                nearestDist = res[0]
                nearest = a
            }
        }
        val txt = nearest?.let {
            val pre = if (nearestDist <= it.radiusMeters * 3) "⚠ cerca: " else ""
            val eta = if (loc.hasSpeed() && loc.speed >= 0.8f) {
                val s = (nearestDist / loc.speed).roundToInt()
                " · ~" + if (s < 60) "${s}s" else "${s / 60} min"
            } else ""
            "$pre${it.name} a ${nearestDist.toInt()} m$eta"
        }
            ?: if (tracking) "Recorridos activos · GPS encendido" else "Vigilando ${watching.size}"
        val title = if (tracking && watching.isEmpty()) {
            "GeoAlarm · recorridos"
        } else {
            "GeoAlarm vigilando (${watching.size})"
        }
        WatchState.update(true, watching.size, txt)
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, WatchService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifications.CH_WATCH)
            .setContentTitle(title)
            .setContentText(txt)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(0, "Detener", stopPi)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, n)
    }

    private fun refreshGpsState() {
        if (isGpsOn()) {
            if (gpsOffNotified) {
                gpsOffNotified = false
                try {
                    (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                        .cancel(GPS_OFF_NOTIF_ID)
                } catch (_: Exception) {}
                WatchState.updateNearest("Obteniendo ubicación…")
            }
            return
        }
        showNoGpsForeground()
        notifyNoGpsOnce()
    }

    private fun showNoGpsForeground() {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val gpsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val gpsPi = PendingIntent.getActivity(this, 3, gpsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifications.CH_WATCH)
            .setContentTitle("GeoAlarm sin GPS")
            .setContentText("Activa ubicación: no podrá avisarte")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(0, "Activar GPS", gpsPi)
            .build()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(NOTIF_ID, n)
        } catch (_: Exception) {}
        WatchState.update(true, watching.size, "GPS apagado – sin avisos")
    }

    private fun notifyNoGpsOnce() {
        if (gpsOffNotified) return
        gpsOffNotified = true
        val gpsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val gpsPi = PendingIntent.getActivity(this, 4, gpsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifications.CH_GPS)
            .setContentTitle("GeoAlarm sin GPS")
            .setContentText("No se te podrá avisar. Toca para activar ubicación.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(gpsPi)
            .setAutoCancel(true)
            .build()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .notify(GPS_OFF_NOTIF_ID, n)
        } catch (_: Exception) {}
    }

    private fun stopWatching() {
        recorder?.stop()
        recorder = null
        tracking = false
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        try {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancel(GPS_OFF_NOTIF_ID)
        } catch (_: Exception) {}
        gpsOffNotified = false
        WatchState.update(false, watching.size)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        recorder?.stop()
        recorder = null
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        try { providersReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        providersReceiver = null
        WatchState.update(false, 0)
        scope.cancel()
        super.onDestroy()
    }
}
