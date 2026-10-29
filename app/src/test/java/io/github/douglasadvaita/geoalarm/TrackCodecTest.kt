package io.github.douglasadvaita.geoalarm

import io.github.douglasadvaita.geoalarm.util.TrackCodec
import io.github.douglasadvaita.geoalarm.util.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class TrackCodecTest {

    @Test
    fun roundTripConservaGeometria() {
        val pts = listOf(
            TrackPoint(-34.9011, -56.1645, 1_000_000L),
            TrackPoint(-34.9016, -56.1648, 1_060_000L),
            TrackPoint(-34.9025, -56.1630, 1_130_000L),
            TrackPoint(-34.9030, -56.1610, 1_200_000L),
            TrackPoint(-34.9031, -56.1590, 1_320_000L)
        )
        val enc = TrackCodec.encode(pts)
        val dec = TrackCodec.decode(pts[0].lat, pts[0].lng, pts[0].atMs, enc)
        assertEquals(pts.size, dec.size)
        for (i in pts.indices) {
            val d = TrackCodec.distanceM(pts[i].lat, pts[i].lng, dec[i].lat, dec[i].lng)
            assertTrue("punto $i desviado $d m", d < 3.0)
            assertEquals(pts[i].atMs, dec[i].atMs)
        }
    }

    @Test
    fun rectaQuedaEnDosPuntos() {
        val line = (0..50).map {
            TrackPoint(-34.9 + it * 0.0001, -56.16, 1_000L + it * 10_000L)
        }
        assertEquals(2, TrackCodec.simplify(line, 10.0).size)
    }

    @Test
    fun curvaConservaVertice() {
        val curve = listOf(
            TrackPoint(0.0, 0.0, 0L),
            TrackPoint(0.0, 0.001, 60_000L),
            TrackPoint(0.001, 0.002, 120_000L)
        )
        assertTrue(TrackCodec.simplify(curve, 10.0).size >= 3)
    }

    @Test
    fun diaDensoEntraEnDiezKb() {
        val pts = (0 until 1_500).map { i ->
            TrackPoint(
                -34.9 + i * 0.00002,
                -56.16 + sin(i / 20.0) * 0.002,
                1_000L + i * 20_000L
            )
        }
        val enc = TrackCodec.encode(pts)
        assertTrue("día de ${enc.length} bytes", enc.length < 10 * 1024)
    }
}
