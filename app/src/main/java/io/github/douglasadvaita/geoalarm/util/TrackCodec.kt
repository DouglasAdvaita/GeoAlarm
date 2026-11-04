package io.github.douglasadvaita.geoalarm.util

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Punto de recorrido con hora
data class TrackPoint(val lat: Double, val lng: Double, val atMs: Long)

// Guarda el día como inicio absoluto + deltas (metros, rumbo, segundos).
// Base64 binario: ~2-4 bytes por vértice. Un día normal queda muy por debajo de 10 KB.
object TrackCodec {
    private const val VERSION = 2
    private const val V1 = 1
    private const val M_PER_DEG_LAT = 110_540.0
    private const val M_PER_DEG_LNG_EQ = 111_320.0

    fun encode(points: List<TrackPoint>): String {
        val pts = points.filter { !it.lat.isNaN() && !it.lng.isNaN() }
        if (pts.size < 2) return ""
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        var prev = pts[0]
        for (i in 1 until pts.size) {
            val p = pts[i]
            // V2: deltas en 1e-5 grados (~1,1 m) con zigzag: sin deriva angular acumulada.
            out.writeSignedVarint(((p.lat - prev.lat) * 1e5).roundToInt())
            out.writeSignedVarint(((p.lng - prev.lng) * 1e5).roundToInt())
            val dt = ((p.atMs - prev.atMs) / 1000L).coerceIn(0L, 86_400L).toInt()
            out.writeVarint(dt)
            prev = p
        }
        return Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    fun decode(startLat: Double, startLng: Double, startMs: Long, encoded: String): List<TrackPoint> {
        if (encoded.isBlank()) return emptyList()
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        if (bytes.isEmpty()) return emptyList()
        val ver = bytes[0].toInt() and 0xFF
        if (ver != VERSION && ver != V1) return emptyList()
        val out = ArrayList<TrackPoint>()
        out.add(TrackPoint(startLat, startLng, startMs))
        var i = 1
        if (ver == V1) {
            var lat = startLat
            var lng = startLng
            var t = startMs
            while (i < bytes.size) {
                val dr = readVarint(bytes, i)
                i = dr.second
                val brg = bytes[i].toInt() and 0xFF
                i++
                val dt = readVarint(bytes, i)
                i = dt.second
                val next = destination(lat, lng, dr.first.toDouble(), brg.toDouble())
                lat = next.first
                lng = next.second
                t += dt.first * 1000L
                out.add(TrackPoint(lat, lng, t))
            }
            return out
        }
        var lat = startLat
        var lng = startLng
        var t = startMs
        while (i < bytes.size) {
            val dLa = readSignedVarint(bytes, i)
            i = dLa.second
            val dLo = readSignedVarint(bytes, i)
            i = dLo.second
            val dt = readVarint(bytes, i)
            i = dt.second
            lat += dLa.first / 1e5
            lng += dLo.first / 1e5
            t += dt.first * 1000L
            out.add(TrackPoint(lat, lng, t))
        }
        return out
    }

    // Douglas-Peucker: en rectas queda solo el inicio y el final; guarda vértices al detectar curva
    fun simplify(points: List<TrackPoint>, toleranceM: Double): List<TrackPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.size - 1))
        while (stack.isNotEmpty()) {
            val seg = stack.removeLast()
            val a = seg[0]
            val b = seg[1]
            var maxD = 0.0
            var idx = -1
            for (k in a + 1 until b) {
                val d = perpendicularM(points[k], points[a], points[b])
                if (d > maxD) {
                    maxD = d
                    idx = k
                }
            }
            if (idx >= 0 && maxD > toleranceM) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx))
                stack.addLast(intArrayOf(idx, b))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * 6_371_000.0 * kotlin.math.asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun destination(lat: Double, lng: Double, distM: Double, bearing: Double): Pair<Double, Double> {
        val rad = Math.toRadians(bearing)
        val dLat = distM * cos(rad) / M_PER_DEG_LAT
        val dLng = distM * sin(rad) / (M_PER_DEG_LNG_EQ * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return (lat + dLat) to (lng + dLng)
    }

    private fun perpendicularM(p: TrackPoint, a: TrackPoint, b: TrackPoint): Double {
        val k = cos(Math.toRadians(a.lat))
        val px = (p.lng - a.lng) * M_PER_DEG_LNG_EQ * k
        val py = (p.lat - a.lat) * M_PER_DEG_LAT
        val bx = (b.lng - a.lng) * M_PER_DEG_LNG_EQ * k
        val by = (b.lat - a.lat) * M_PER_DEG_LAT
        val len = hypot(bx, by)
        if (len < 0.001) return hypot(px, py)
        return abs(px * by - py * bx) / len
    }

    private fun ByteArrayOutputStream.writeSignedVarint(value: Int) {
        writeVarint((value shl 1) xor (value shr 31))
    }

    private fun readSignedVarint(bytes: ByteArray, start: Int): Pair<Int, Int> {
        val r = readVarint(bytes, start)
        val n = r.first
        return ((n ushr 1) xor -(n and 1)) to r.second
    }

    private fun ByteArrayOutputStream.writeVarint(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        write(v and 0x7F)
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            i++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to i
    }
}
