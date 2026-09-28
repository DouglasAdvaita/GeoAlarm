package io.github.douglasadvaita.geoalarm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object WatchState {
    private val _isWatching = MutableStateFlow(false)
    val isWatching: StateFlow<Boolean> = _isWatching

    private val _enabledCount = MutableStateFlow(0)
    val enabledCount: StateFlow<Int> = _enabledCount

    private val _nearestText = MutableStateFlow("")
    val nearestText: StateFlow<String> = _nearestText

    fun update(watching: Boolean, count: Int, nearest: String = "") {
        _isWatching.value = watching
        _enabledCount.value = count
        if (nearest.isNotEmpty()) _nearestText.value = nearest
        if (!watching) _nearestText.value = ""
    }

    fun updateNearest(nearest: String) {
        _nearestText.value = nearest
    }
}
