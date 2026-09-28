package io.github.douglasadvaita.geoalarm.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
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
import io.github.douglasadvaita.geoalarm.ui.AlarmRingActivity
import io.github.douglasadvaita.geoalarm.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

class WatchService : Service() {

    companion object {
        const val ACTION_START = "io.github.douglasadvaita.geoalarm.START"
        const val ACTION_STOP = "io.github.douglasadvaita.geoalarm.STOP"
        const val ACTION_SNOOZE_5 = "io.github.douglasadvaita.geoalarm.SNOOZE_5"
        const val NOTIF_ID = 1001

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

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            checkTriggers(loc)
            updateNotification(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatching()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE_5 -> {
                val id = intent.getLongExtra("alarmId", 0L)
                scope.launch {
                    if (id > 0) {
                        val dao = AppDatabase.get(this@WatchService).alarmDao()
                        dao.getById(id)?.let {
                            dao.update(it.copy(snoozedUntil = System.currentTimeMillis() + 5 * 60 * 1000))
                        }
                        watching = dao.getEnabled()
                    }
                }
                return START_STICKY
            }
            else -> startWatching()
        }
        return START_STICKY
    }

    private fun startWatching() {
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
            WatchState.update(watching.isNotEmpty(), watching.size)
            if (watching.isEmpty()) {
                stopWatching()
                return@launch
            }
            val hasBus = watching.any { it.type == Alarm.TYPE_BUS }
            val interval = if (hasBus) 5_000L else 30_000L
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
                .setMinUpdateDistanceMeters(10f)
                .build()
            try {
                fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                stopWatching()
            }
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
                val outside = dist > a.radiusMeters * 1.2f
                if (outside) {
                    wasOutside[a.id] = true
                    continue
                }
                // Dentro: exige haber estado fuera antes (evita disparo instantáneo al abrir app dentro)
                if (wasOutside[a.id] != true) {
                    // Si venía de snooze vencido y sigue dentro, espera a salir
                    continue
                }
                fire(a, dist)
                // No se deshabilita: pasa a descansar hasta mañana 06:00
                val updated = a.copy(snoozedUntil = nextMorning6am())
                dao.update(updated)
                watching = watching.map { if (it.id == a.id) updated else it }
                wasOutside[a.id] = false
                WatchState.update(true, watching.size)
                break
            }
        }
    }

    private fun fire(a: Alarm, distM: Float) {
        if (a.type == Alarm.TYPE_BUS) {
            // Full-screen intent: el sistema abre aunque esté bloqueado (Android 10+)
            val full = Intent(this, AlarmRingActivity::class.java).apply {
                putExtra("name", a.name)
                putExtra("message", a.message)
                putExtra("dist", distM)
                putExtra("alarmId", a.id)
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
        val txt = nearest?.let { "${it.name} a ${nearestDist.toInt()} m" }
            ?: "Vigilando ${watching.size}"
        WatchState.update(true, watching.size, txt)
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, WatchService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifications.CH_WATCH)
            .setContentTitle("GeoAlarm vigilando (${watching.size})")
            .setContentText(txt)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .addAction(0, "Detener", stopPi)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, n)
    }

    private fun stopWatching() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        WatchState.update(false, watching.size)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        WatchState.update(false, 0)
        scope.cancel()
        super.onDestroy()
    }
}
