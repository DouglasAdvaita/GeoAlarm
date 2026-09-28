package io.github.douglasadvaita.geoalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // v1: no auto-inicia vigilancia (transparente para Play).
            // Las alarmas quedan guardadas en Room y el usuario las reactiva.
        }
    }
}
