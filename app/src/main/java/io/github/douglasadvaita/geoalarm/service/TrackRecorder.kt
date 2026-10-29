package io.github.douglasadvaita.geoalarm.service

import android.content.Context
import android.location.Location
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.data.TrackStay
import io.github.douglasadvaita.geoalarm.data.TrackPointRecord
import io.github.douglasadvaita.geoalarm.util.TrackCodec
import io.github.douglasadvaita.geoalarm.util.TrackDates
import io.github.douglasadvaita.geoalarm.util.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// Guarda cada punto del recorrido durante el día y detecta permanencias.
// Muestreo adaptativo: lo maneja WatchService según la velocidad.
class TrackRecorder(context: Context) {
    private val app = context.applicationContext
    private val dao = AppDatabase.get(app).trackDao()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val raw = ArrayList<TrackPoint>()
    private var dayKey = 0
    private var distanceM = 0.0
    private var lastFixMs = 0L
    private var stay: StayBuilder? = null

    private data class StayBuilder(
        val lat: Double,
        val lng: Double,
        val startMs: Long,
        var lastMs: Long,
        var fixes: Int
    )

    fun onFix(loc: Location) {
        val acc = if (loc.hasAccuracy()) loc.accuracy else 999f
        if (acc > 60f) return
        val t = loc.time
        if (t <= lastFixMs) return

        val p = TrackPoint(loc.latitude, loc.longitude, t)
        val k = TrackDates.dayKey(t)
        if (dayKey == 0) dayKey = k
        if (k != dayKey) {
            endStay(dayKey)
            dayKey = k
            raw.clear()
            distanceM = 0.0
        }

        val prev = raw.lastOrNull()
        if (prev != null) {
            val d = TrackCodec.distanceM(prev.lat, prev.lng, p.lat, p.lng)
            val dtSec = ((t - prev.atMs) / 1000.0).coerceAtLeast(1.0)
            if (d / dtSec > 72.0) return // salto imposible (>260 km/h)
            if (d >= 3.0) distanceM += d // ignora ruido quieto
        }
        raw.add(p)
        lastFixMs = t
        io.launch {
            try {
                dao.insertPoint(TrackPointRecord(dayKey = k, lat = p.lat, lng = p.lng, atMs = p.atMs))
            } catch (_: Exception) {}
        }
        updateStay(p)
    }

    // Permanencia: 10 min o más dentro de ~45 m; radio crece con el tiempo
    private fun updateStay(p: TrackPoint) {
        val s = stay
        val d = if (s == null) Float.MAX_VALUE
        else TrackCodec.distanceM(s.lat, s.lng, p.lat, p.lng).toFloat()
        if (s != null && d <= 45f) {
            s.lastMs = p.atMs
            s.fixes++
            return
        }
        endStay(dayKey)
        stay = StayBuilder(p.lat, p.lng, p.atMs, p.atMs, 1)
    }

    private fun endStay(day: Int) {
        val s = stay ?: return
        stay = null
        val dur = s.lastMs - s.startMs
        if (dur < 10 * 60_000L) return
        val hours = (dur / 3_600_000.0).coerceIn(0.0, 24.0)
        val radius = (40.0 + 110.0 * sqrt(hours / 24.0)).coerceAtMost(150.0).toFloat()
        io.launch {
            try {
                dao.insertStay(
                    TrackStay(
                        dayKey = day,
                        lat = s.lat,
                        lng = s.lng,
                        startMs = s.startMs,
                        endMs = s.lastMs,
                        radiusM = radius
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        endStay(dayKey)
    }
}
