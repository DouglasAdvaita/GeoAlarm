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
    private const val K_TRIPLE = "triple"
    private const val K_PROX = "proximity"

    data class Cfg(
        val ringtoneUri: String? = null,
        val boostVolume: Boolean = true,
        val strongVib: Boolean = true,
        val tts: Boolean = true,
        val tripleConfirm: Boolean = true,
        // Avisos de voz extra al acercarse (60%, 30% del radio y 50 m)
        val proximity: Boolean = true
    )

    fun load(ctx: Context): Cfg {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        return Cfg(
            ringtoneUri = s.getString(K_URI, null),
            boostVolume = s.getBoolean(K_BOOST, true),
            strongVib = s.getBoolean(K_VIB, true),
            tts = s.getBoolean(K_TTS, true),
            tripleConfirm = s.getBoolean(K_TRIPLE, true),
            proximity = s.getBoolean(K_PROX, true)
        )
    }

    fun save(ctx: Context, c: Cfg) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(K_URI, c.ringtoneUri)
            .putBoolean(K_BOOST, c.boostVolume)
            .putBoolean(K_VIB, c.strongVib)
            .putBoolean(K_TTS, c.tts)
            .putBoolean(K_TRIPLE, c.tripleConfirm)
            .putBoolean(K_PROX, c.proximity)
            .apply()
    }

    fun defaultAlarmUri(ctx: Context): android.net.Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}
