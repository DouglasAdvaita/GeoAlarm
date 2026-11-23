package io.github.douglasadvaita.geoalarm.util

import java.util.Locale
import kotlin.math.roundToInt

// Texto corto y uniforme para el trote (botón, tarjetas e historial)
object TrotFormat {

    fun distance(meters: Double): String {
        if (meters < 1000.0) return "${meters.roundToInt()} m"
        return String.format(Locale.US, "%.2f km", meters / 1000.0).replace('.', ',')
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun speed(distanceM: Double, ms: Long): String {
        if (ms <= 0L) return "0,0 km/h"
        val kmh = distanceM / 1000.0 / (ms / 3_600_000.0)
        return String.format(Locale.US, "%.1f km/h", kmh).replace('.', ',')
    }

    fun summary(distanceM: Double, ms: Long): String =
        "${distance(distanceM)} · ${duration(ms)} · ${speed(distanceM, ms)}"
}
