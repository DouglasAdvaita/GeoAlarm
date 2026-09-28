package io.github.douglasadvaita.geoalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.douglasadvaita.geoalarm.data.Alarm
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).alarmDao()

    val alarms = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(name: String, message: String, lat: Double, lng: Double, radius: Int, type: String, repeat: Boolean, editId: Long? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (editId != null && editId > 0) {
                val old = dao.getById(editId)
                if (old != null) dao.update(old.copy(name = name, message = message, lat = lat, lng = lng, radiusMeters = radius, type = type, repeat = repeat, snoozedUntil = 0L))
            } else {
                dao.upsert(Alarm(name = name, message = message, lat = lat, lng = lng, radiusMeters = radius, type = type, repeat = repeat))
            }
            onDone()
        }
    }

    fun updateMessage(a: Alarm, message: String) {
        viewModelScope.launch { dao.update(a.copy(message = message)) }
    }

    fun reactivateExpired() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (a in dao.getEnabled()) {
                if (a.snoozedUntil > 0 && a.snoozedUntil <= now) {
                    dao.update(a.copy(snoozedUntil = 0L))
                }
            }
        }
    }

    fun toggle(a: Alarm) {
        viewModelScope.launch { dao.update(a.copy(enabled = !a.enabled)) }
    }

    fun delete(a: Alarm) {
        viewModelScope.launch { dao.delete(a) }
    }
}
