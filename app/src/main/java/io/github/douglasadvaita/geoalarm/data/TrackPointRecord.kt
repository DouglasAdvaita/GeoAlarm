package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "track_points", indices = [Index("dayKey", "atMs")])
data class TrackPointRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: Int,
    val lat: Double,
    val lng: Double,
    val atMs: Long
)
