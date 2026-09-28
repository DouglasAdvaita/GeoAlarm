package io.github.douglasadvaita.geoalarm

import android.app.Application
import org.osmdroid.config.Configuration

class GeoAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = "GeoAlarm/1.0"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }
}
