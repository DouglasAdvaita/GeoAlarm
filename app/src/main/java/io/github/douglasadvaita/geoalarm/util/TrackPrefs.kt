package io.github.douglasadvaita.geoalarm.util

import android.content.Context

object TrackPrefs {
    private const val P = "track_prefs"
    private const val K_ON = "enabled"

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean(K_ON, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putBoolean(K_ON, on).apply()
    }
}
