package io.github.douglasadvaita.geoalarm.util

import android.content.Context
import android.media.RingtoneManager

// Config global Bus: lo que se elige en un pedido Bus vale para todos
// hasta que otro pedido Bus lo cambie. Sin tocar DB.
object BusAlertPrefs {
    private const val P = "bus_alert"
    private const val K_URI = "ringtoneUri"
    private const val K_BOOST = "boostVolume"
    private const val K_VIB = "strongVib"
    private const val K_TTS = "tts"
    private const val K_FLASH = "flash"
    private const val K_TRIPLE = "triple"

    data class Cfg(
        val ringtoneUri: String? = null,
        val boostVolume: Boolean = true,
        val strongVib: Boolean = true,
        val tts: Boolean = true,
        val flash: Boolean = true,
        val tripleConfirm: Boolean = true
    )

    fun load(ctx: Context): Cfg {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        return Cfg(
            ringtoneUri = s.getString(K_URI, null),
            boostVolume = s.getBoolean(K_BOOST, true),
            strongVib = s.getBoolean(K_VIB, true),
            tts = s.getBoolean(K_TTS, true),
            flash = s.getBoolean(K_FLASH, true),
            tripleConfirm = s.getBoolean(K_TRIPLE, true)
        )
    }

    fun save(ctx: Context, c: Cfg) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(K_URI, c.ringtoneUri)
            .putBoolean(K_BOOST, c.boostVolume)
            .putBoolean(K_VIB, c.strongVib)
            .putBoolean(K_TTS, c.tts)
            .putBoolean(K_FLASH, c.flash)
            .putBoolean(K_TRIPLE, c.tripleConfirm)
            .apply()
    }

    fun defaultAlarmUri(ctx: Context): android.net.Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}
