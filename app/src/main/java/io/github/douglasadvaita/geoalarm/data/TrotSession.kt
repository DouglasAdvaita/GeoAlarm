package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Trote registrado: fecha de inicio, lugar de salida y resultados finales
@Entity(tableName = "trots", indices = [Index("startMs")])
data class TrotSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val distanceM: Double,
    val startLat: Double,
    val startLng: Double,
    val startPlace: String
)
