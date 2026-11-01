package fi.aikapolku.app

import androidx.compose.ui.geometry.Offset
import fi.aikapolku.app.timer.dialMinuteFromPoint
import fi.aikapolku.app.timer.dialTapMinute
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kellotaulun kosketus -> minuutit (dialMinuteFromPoint).
 * Kellotaulu on 60 minuutin asteikko, jossa aika kasvaa vastapäivään ylhäältä.
 * Puhdas laskenta: ei Androidia, ei Robolectricia.
 */
class DialMinuteTest {

    private val size = 200
    private val c = size / 2f

    private fun minuteAt(x: Float, y: Float) = dialMinuteFromPoint(Offset(x, y), size, size)

    @Test
    fun `ylin kohta klo 12 on 60 minuuttia`() {
        assertEquals(60, minuteAt(c, 0f))
    }

    @Test
    fun `vasen reuna klo 9 on 15 minuuttia`() {
        assertEquals(15, minuteAt(0f, c))
    }

    @Test
    fun `alin kohta klo 6 on 30 minuuttia`() {
        assertEquals(30, minuteAt(c, size.toFloat()))
    }

    @Test
    fun `oikea reuna klo 3 on 45 minuuttia`() {
        assertEquals(45, minuteAt(size.toFloat(), c))
    }

    @Test
    fun `heti klo 12 vasemmalla puolella on 1 minuutti`() {
        assertEquals(1, minuteAt(c - 1f, 0f))
    }

    @Test
    fun `heti klo 12 oikealla puolella on 60 minuuttia`() {
        assertEquals(60, minuteAt(c + 1f, 0f))
    }

    @Test
    fun `tulos on aina valilla 1-60 koko kellotaulun alueella`() {
        for (x in 0..size step 5) for (y in 0..size step 5) {
            val m = minuteAt(x.toFloat(), y.toFloat())
            assertTrue("($x, $y) -> $m", m in 1..60)
        }
    }

    @Test
    fun `tulos ei riipu kellotaulun koosta`() {
        val small = dialMinuteFromPoint(Offset(0f, 50f), 100, 100)
        val large = dialMinuteFromPoint(Offset(0f, 500f), 1000, 1000)
        assertEquals(small, large)
    }

    // --- Napautus: lähin 5 minuuttia ---

    /** Piste kellotaululla kohdassa [minutes] (desimaaleina), säde 80 % reunasta. */
    private fun tapAt(minutes: Double): Int {
        val angle = Math.toRadians(270.0 - minutes * 6.0)
        val r = c * 0.8f
        return dialTapMinute(Offset(c + r * cos(angle).toFloat(), c + r * sin(angle).toFloat()), size, size)
    }

    @Test
    fun `napautus 45 kohdalle asettaa 45 minuuttia vaikka osuma heittaa`() {
        assertEquals(45, tapAt(45.0))
        assertEquals(45, tapAt(43.0))
        assertEquals(45, tapAt(47.2))
    }

    @Test
    fun `napautus 15 kohdalle asettaa 15 minuuttia`() {
        assertEquals(15, tapAt(15.0))
        assertEquals(15, tapAt(13.6))
        assertEquals(15, tapAt(16.9))
    }

    @Test
    fun `napautus ylimpaan kohtaan on 60 minuuttia`() {
        assertEquals(60, tapAt(0.3))
        assertEquals(60, tapAt(59.0))
    }

    @Test
    fun `napautus antaa aina 5 minuutin kerrannaisen valilta 5-60`() {
        for (x in 0..size step 7) for (y in 0..size step 7) {
            val m = dialTapMinute(Offset(x.toFloat(), y.toFloat()), size, size)
            assertTrue("($x, $y) -> $m", m in 5..60 && m % 5 == 0)
        }
    }
}
