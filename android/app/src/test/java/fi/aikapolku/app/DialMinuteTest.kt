package fi.aikapolku.app

import androidx.compose.ui.geometry.Offset
import fi.aikapolku.app.timer.dialMinuteFromPoint
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
}
