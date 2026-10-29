package io.github.douglasadvaita.geoalarm.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.douglasadvaita.geoalarm.service.TrackCompactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class TrackCompactionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { TrackCompactor.compactPreviousDay(context) } finally { pending.finish() }
        }
        schedule(context)
    }

    companion object {
        private const val ACTION = "io.github.douglasadvaita.geoalarm.COMPACT_TRACK"

        fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 405, Intent(context, TrackCompactionReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val whenMs = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 5)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }
}
