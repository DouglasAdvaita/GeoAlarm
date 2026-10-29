package io.github.douglasadvaita.geoalarm.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notifications {
    const val CH_WATCH = "watch"
    const val CH_ALARM = "alarm"
    const val CH_REMINDER = "reminder"
    const val CH_GPS = "gps_off"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Vigilancia", NotificationManager.IMPORTANCE_LOW)
        )
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Alarma bus", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_GPS, "Sin GPS", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
