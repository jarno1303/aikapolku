package fi.aikapolku.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ennakkovaroituksen sääntö (tutkielma A1/A2). */
class TransitionWarningTest {
    private val min = 60_000L

    @Test
    fun `nakyy kun aika kay ja jaljella on enintaan varoitusaika`() {
        assertTrue(TimeDisplayPrefs.showSoon(5 * min, 10 * min, running = true, warnMinutes = 5))
        assertTrue(TimeDisplayPrefs.showSoon(1_000L, 10 * min, running = true, warnMinutes = 5))
    }

    @Test
    fun `ei nay ennen varoitusaikaa eika ajan loputtua`() {
        assertFalse(TimeDisplayPrefs.showSoon(5 * min + 1, 10 * min, running = true, warnMinutes = 5))
        assertFalse(TimeDisplayPrefs.showSoon(0L, 10 * min, running = true, warnMinutes = 5))
    }

    @Test
    fun `ei nay pysaytettyna eika kun asetus on pois`() {
        assertFalse(TimeDisplayPrefs.showSoon(2 * min, 10 * min, running = false, warnMinutes = 5))
        assertFalse(TimeDisplayPrefs.showSoon(2 * min, 10 * min, running = true, warnMinutes = 0))
    }

    @Test
    fun `lyhyessa vaiheessa ei varoitusta heti alusta`() {
        // 4 minuutin vaihe ja 5 minuutin varoitus: varoitus näkyisi koko ajan, joten ei näytetä.
        assertFalse(TimeDisplayPrefs.showSoon(3 * min, 4 * min, running = true, warnMinutes = 5))
        // Sama vaihe 2 minuutin varoituksella toimii.
        assertTrue(TimeDisplayPrefs.showSoon(2 * min, 4 * min, running = true, warnMinutes = 2))
    }
}
