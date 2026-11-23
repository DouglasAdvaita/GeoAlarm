package io.github.douglasadvaita.geoalarm.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.douglasadvaita.geoalarm.MainActivity
import io.github.douglasadvaita.geoalarm.R
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.data.TrotSession
import io.github.douglasadvaita.geoalarm.util.Notifications
import io.github.douglasadvaita.geoalarm.util.TrackCodec
import io.github.douglasadvaita.geoalarm.util.TrotFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Registra un trote: tiempo, distancia, velocidad promedio y lugar de salida.
// Servicio en primer plano para que el GPS siga con la pantalla apagada.
class TrotService : Service() {

    companion object {
        const val ACTION_START = "io.github.douglasadvaita.geoalarm.TROT_START"
        const val ACTION_STOP = "io.github.douglasadvaita.geoalarm.TROT_STOP"
        const val NOTIF_ID = 2001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var startMs = 0L
    private var distanceM = 0.0
    private var startLat = 0.0
    private var startLng = 0.0
    private var startPlace = ""
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastAtMs = 0L
    private var hasLast = false
    private var ticker: Job? = null
    private var finishing = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val acc = if (loc.hasAccuracy()) loc.accuracy else 999f
            if (acc > 50f) return
            val t = System.currentTimeMillis()

            if (!hasLast) {
                hasLast = true
                startLat = loc.latitude
                startLng = loc.longitude
                lastLat = loc.latitude
                lastLng = loc.longitude
                lastAtMs = t
                resolveStartPlace(loc.latitude, loc.longitude)
                return
            }

            val d = TrackCodec.distanceM(lastLat, lastLng, loc.latitude, loc.longitude).toDouble()
            val dt = ((t - lastAtMs) / 1000.0).coerceAtLeast(1.0)
            if (d / dt > 12.5) return // salto imposible para un trote (>45 km/h)
            if (d >= 3.0) {
                distanceM += d
                lastLat = loc.latitude
                lastLng = loc.longitude
                lastAtMs = t
                TrotState.updateDistance(distanceM)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishTrot()
                return START_NOT_STICKY
            }
            else -> beginTrot()
        }
        return START_NOT_STICKY
    }

    private fun beginTrot() {
        if (startMs != 0L) return
        startMs = System.currentTimeMillis()
        TrotState.start(startMs)
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (_: Exception) {
            TrotState.finish(0L, 0.0)
            stopSelf()
            return
        }
        startLocationUpdates()
        ticker = scope.launch {
            while (isActive) {
                delay(5_000L)
                updateNotification()
            }
        }
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Sin permiso de ubicación: se registra solo el tiempo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun finishTrot() {
        if (finishing) return
        finishing = true
        ticker?.cancel()
        ticker = null
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        if (startMs == 0L) {
            stopSelf()
            return
        }
        val endMs = System.currentTimeMillis()
        val duration = (endMs - startMs).coerceAtLeast(0L)
        val totalDistance = distanceM
        val lat = startLat
        val lng = startLng
        val knownPlace = startPlace
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        scope.launch(Dispatchers.IO) {
            val place = if (knownPlace.isNotBlank()) knownPlace else resolvePlace(lat, lng)
            try {
                AppDatabase.get(this@TrotService).trackDao().insertTrot(
                    TrotSession(
                        startMs = startMs,
                        endMs = endMs,
                        durationMs = duration,
                        distanceM = totalDistance,
                        startLat = lat,
                        startLng = lng,
                        startPlace = place
                    )
                )
            } catch (_: Exception) {}
            TrotState.finish(duration, totalDistance)
            stopSelf()
        }
    }

    private fun resolveStartPlace(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            val place = resolvePlace(lat, lng)
            if (place.isNotBlank()) {
                startPlace = place
                TrotState.updatePlace(place)
            }
        }
    }

    private fun resolvePlace(lat: Double, lng: Double): String {
        if (lat == 0.0 && lng == 0.0) return ""
        return try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val address = Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                val name = listOfNotNull(address?.thoroughfare, address?.locality)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                name.ifBlank { coords(lat, lng) }
            } else {
                coords(lat, lng)
            }
        } catch (_: Exception) {
            coords(lat, lng)
        }
    }

    private fun coords(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.4f, %.4f", lat, lng)

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrotService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        return NotificationCompat.Builder(this, Notifications.CH_TROT)
            .setContentTitle("Trotando")
            .setContentText(TrotFormat.summary(distanceM, elapsed))
            .setSmallIcon(R.mipmap.ic_notification)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .build()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        ticker?.cancel()
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }
}
