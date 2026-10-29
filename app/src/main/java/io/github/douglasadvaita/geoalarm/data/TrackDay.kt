package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Un día de recorrido comprimido: inicio absoluto + vértices en dist/ángulo (base64)
@Entity(tableName = "track_days")
data class TrackDay(
    // yyyyMMdd local
    @PrimaryKey val dayKey: Int,
    val startLat: Double,
    val startLng: Double,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val encoded: String
)
