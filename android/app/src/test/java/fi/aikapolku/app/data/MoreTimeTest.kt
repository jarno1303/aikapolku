package fi.aikapolku.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Tarvitsen vielä hetken" -säännöt (tutkielma A2). Puhdas JVM-testi. */
class MoreTimeTest {
    private val min = 60_000L

    @Test
    fun `nakyy ennakkovaroituksen aikana eika ennen sita`() {
        assertFalse(MoreTime.visible(6 * min, 20 * min, running = true, ended = false, warnMinutes = 5))
        assertTrue(MoreTime.visible(5 * min, 20 * min, running = true, ended = false, warnMinutes = 5))
        assertTrue(MoreTime.visible(1_000L, 20 * min, running = true, ended = false, warnMinutes = 5))
    }

    @Test
    fun `nakyy aina kun aika on loppunut`() {
        assertTrue(MoreTime.visible(0L, 10 * min, running = false, ended = true, warnMinutes = 0))
    }

    @Test
    fun `ei nay pysaytettyna`() {
        assertFalse(MoreTime.visible(1 * min, 10 * min, running = false, ended = false, warnMinutes = 5))
    }

    @Test
    fun `ilman ennakkovaroitusta nakyy kahden viimeisen minuutin aikana`() {
        assertFalse(MoreTime.visible(3 * min, 10 * min, running = true, ended = false, warnMinutes = 0))
        assertTrue(MoreTime.visible(2 * min, 10 * min, running = true, ended = false, warnMinutes = 0))
    }

    @Test
    fun `lyhyessa toiminnossa ei heti alusta`() {
        // 4 minuutin toiminto ja 5 minuutin varoitus: painike vasta toisella puoliskolla.
        assertFalse(MoreTime.visible(3 * min, 4 * min, running = true, ended = false, warnMinutes = 5))
        assertTrue(MoreTime.visible(2 * min, 4 * min, running = true, ended = false, warnMinutes = 5))
    }

    @Test
    fun `lisaaika annetaan ohjaajan rajoissa`() {
        assertTrue(MoreTime.canGrant(used = 0, minutes = 3, count = 1))
        assertFalse(MoreTime.canGrant(used = 1, minutes = 3, count = 1))
        assertTrue(MoreTime.canGrant(used = 1, minutes = 3, count = 2))
        assertFalse(MoreTime.canGrant(used = 2, minutes = 3, count = 2))
    }

    @Test
    fun `ei lisaaikaa kun ohjaaja ei salli`() {
        assertFalse(MoreTime.canGrant(used = 0, minutes = 0, count = 2))
    }

    @Test
    fun `oletukset kuuluvat vaihtoehtoihin`() {
        assertTrue(MoreTime.MINUTES_DEFAULT in MoreTime.MINUTE_OPTIONS)
        assertTrue(MoreTime.COUNT_DEFAULT in MoreTime.COUNT_OPTIONS)
    }
}
