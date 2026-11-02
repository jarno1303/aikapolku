package fi.aikapolku.app

import fi.aikapolku.app.timer.ScreenOn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Näyttö päällä: useampi samanaikainen aika, näyttö vapautuu vasta viimeisen loputtua. */
class ScreenOnTest {
    @Test
    fun `naytto vapautuu vasta kun mikaan aika ei ole kaynnissa`() {
        ScreenOn.update(null, "program", true)
        ScreenOn.update(null, "timer", true)
        ScreenOn.update(null, "program", false)
        assertTrue(ScreenOn.any())
        assertFalse(ScreenOn.active("program"))
        ScreenOn.update(null, "timer", false)
        assertFalse(ScreenOn.any())
    }
}
