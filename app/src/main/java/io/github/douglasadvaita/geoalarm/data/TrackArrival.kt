package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Sonada de alarma: destino, nota, ubicación y momento exacto del aviso
@Entity(tableName = "track_arrivals", indices = [Index("dayKey"), Index("arrivedAtMs")])
data class TrackArrival(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: Int,
    val alarmId: Long,
    val destination: String,
    val message: String,
    val lat: Double,
    val lng: Double,
    val arrivedAtMs: Long
)
