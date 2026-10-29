package io.github.douglasadvaita.geoalarm.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.douglasadvaita.geoalarm.data.TrackDay
import io.github.douglasadvaita.geoalarm.data.TrackStay
import java.io.File
import java.time.Instant
import java.util.Locale

object GpxExport {
    fun build(days: List<TrackDay>, stays: List<TrackStay>): String {
        val sb = StringBuilder(16_384)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GeoAlarm\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (s in stays) {
            sb.append("  <wpt lat=\"").append(fmt(s.lat)).append("\" lon=\"").append(fmt(s.lng)).append("\">\n")
            sb.append("    <name>Permanencia ").append(TrackDates.dateOf(s.dayKey)).append("</name>\n")
            sb.append("    <desc>").append(duration(s.endMs - s.startMs))
                .append(" · radio ").append(s.radiusM.toInt()).append(" m</desc>\n")
            sb.append("  </wpt>\n")
        }
        for (d in days) {
            val pts = TrackCodec.decode(d.startLat, d.startLng, d.startMs, d.encoded)
            if (pts.isEmpty()) continue
            sb.append("  <trk>\n    <name>").append(TrackDates.dateOf(d.dayKey)).append(" ")
                .append(TrackDates.weekdayShort(d.dayKey)).append("</name>\n    <trkseg>\n")
            for (p in pts) {
                sb.append("      <trkpt lat=\"").append(fmt(p.lat)).append("\" lon=\"").append(fmt(p.lng))
                    .append("\"><time>").append(Instant.ofEpochMilli(p.atMs)).append("</time></trkpt>\n")
            }
            sb.append("    </trkseg>\n  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun write(ctx: Context, name: String, content: String): File? {
        return try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            File(dir, name).apply { writeText(content) }
        } catch (_: Exception) {
            null
        }
    }

    fun share(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(i, "Exportar recorridos"))
        } catch (_: Exception) {}
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun duration(ms: Long): String {
        val min = (ms / 60_000L).coerceAtLeast(0)
        return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
    }
}
