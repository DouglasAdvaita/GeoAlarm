package io.github.douglasadvaita.geoalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val message: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int,
    // "BUS" o "REMINDER"
    val type: String,
    val enabled: Boolean = true,
    val repeat: Boolean = false,
    // Timestamp hasta cuando descansa tras disparar (mañana 06:00). 0 = activa.
    val snoozedUntil: Long = 0L
) {
    companion object {
        const val TYPE_BUS = "BUS"
        const val TYPE_REMINDER = "REMINDER"
        const val DEFAULT_BUS_RADIUS = 500
        const val DEFAULT_REMINDER_RADIUS = 30
    }
}
