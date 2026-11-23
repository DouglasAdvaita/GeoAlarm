package io.github.douglasadvaita.geoalarm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Estado del trote: lo publica TrotService y lo consume la pantalla principal
data class TrotUi(
    val active: Boolean = false,
    val startMs: Long = 0L,
    val distanceM: Double = 0.0,
    val place: String = "",
    // Último resultado final, para mostrarlo en letra chica al detener
    val lastDurationMs: Long = 0L,
    val lastDistanceM: Double = 0.0
)

object TrotState {
    private val _ui = MutableStateFlow(TrotUi())
    val ui: StateFlow<TrotUi> = _ui

    fun start(startMs: Long) {
        _ui.value = TrotUi(active = true, startMs = startMs)
    }

    fun updateDistance(distanceM: Double) {
        val s = _ui.value
        if (s.active) _ui.value = s.copy(distanceM = distanceM)
    }

    fun updatePlace(place: String) {
        val s = _ui.value
        if (s.active && place.isNotBlank()) _ui.value = s.copy(place = place)
    }

    fun finish(durationMs: Long, distanceM: Double) {
        _ui.value = TrotUi(
            active = false,
            lastDurationMs = durationMs,
            lastDistanceM = distanceM
        )
    }
}
