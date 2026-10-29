package io.github.douglasadvaita.geoalarm.service

import android.content.Context
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.data.TrackDay
import io.github.douglasadvaita.geoalarm.util.TrackCodec
import io.github.douglasadvaita.geoalarm.util.TrackDates
import io.github.douglasadvaita.geoalarm.util.TrackPoint

object TrackCompactor {
    suspend fun compactDay(context: Context, dayKey: Int) {
        val dao = AppDatabase.get(context.applicationContext).trackDao()
        val records = dao.getPoints(dayKey)
        if (records.size < 2) return
        val points = records.map { TrackPoint(it.lat, it.lng, it.atMs) }
        val compact = TrackCodec.simplify(points, 10.0)
        if (compact.size < 2) return
        var distance = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = TrackCodec.distanceM(a.lat, a.lng, b.lat, b.lng)
            if (d >= 3.0) distance += d
        }
        dao.upsertDay(
            TrackDay(
                dayKey = dayKey,
                startLat = points.first().lat,
                startLng = points.first().lng,
                startMs = points.first().atMs,
                endMs = points.last().atMs,
                distanceM = distance,
                encoded = TrackCodec.encode(compact)
            )
        )
        dao.deletePoints(dayKey)
    }

    suspend fun compactPreviousDay(context: Context) {
        val previous = java.time.LocalDate.now().minusDays(1)
        compactDay(context, TrackDates.dayKey(previous))
    }
}
