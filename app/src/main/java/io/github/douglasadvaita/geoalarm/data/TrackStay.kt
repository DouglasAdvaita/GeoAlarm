package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Permanencia: lugar donde se estuvo quieto >= 10 min; radio crece con el tiempo
@Entity(tableName = "track_stays", indices = [Index("dayKey")])
data class TrackStay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: Int,
    val lat: Double,
    val lng: Double,
    val startMs: Long,
    val endMs: Long,
    val radiusM: Float
)
