package fi.aikapolku.app

import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.firstthen.EarlyDone
import fi.aikapolku.app.firstthen.FlowTimer
import fi.aikapolku.app.programs.run.DoneGate
import fi.aikapolku.app.timer.TimerPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensin–Sitten: Valmis ennen ajan loppua samalla asetuksella kuin päiväohjelmassa (15.11.2026). */
class EarlyDoneTest {
    private class FakeTimer : TimerPort {
        var clock = 0L
        var until: Long? = null
        var completed = 0
        override fun now() = clock
        override fun allowed() = true
        override fun requestPermissions() {}
        override fun start(durationMs: Long, title: String): Long { until = clock + durationMs; return until!! }
        override fun stop(remainingMs: Long) { until = null }
        override fun remaining(fallbackMs: Long) = until?.let { (it - clock).coerceAtLeast(0L) } ?: fallbackMs
        override fun isRunning() = until != null
        override fun complete(): Boolean { completed++; val first = until != null; until = null; return first }
    }

    @Test
    fun `asiakkaan asetus ratkaisee, ohjaaja saa aina kysyen`() {
        assertEquals(DoneGate.ALLOW, EarlyDone.gate(RunPreferences.DONE_ALWAYS, caregiverOpen = false, moreTimeUsed = 0))
        assertEquals(DoneGate.CONFIRM, EarlyDone.gate(RunPreferences.DONE_CONFIRM, caregiverOpen = false, moreTimeUsed = 0))
        assertNull(EarlyDone.gate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false, moreTimeUsed = 0))
        assertEquals(DoneGate.CONFIRM, EarlyDone.gate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = true, moreTimeUsed = 0))
        assertEquals(DoneGate.CONFIRM, EarlyDone.gate(RunPreferences.DONE_ALWAYS, caregiverOpen = true, moreTimeUsed = 0))
    }

    @Test
    fun `lisaajan pyytanyt asiakas saa lopettaa heti`() {
        assertEquals(DoneGate.ALLOW, EarlyDone.gate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false, moreTimeUsed = 1))
    }

    @Test
    fun `valmis etuajassa lopettaa ajan ilman aanimerkkia ja ilman lisaaikaa`() {
        val port = FakeTimer()
        val t = FlowTimer(port)
        t.prepareForActivity("Askartelu", 10)
        t.startPause("Ajastin") {}
        port.clock = 3 * 60_000L
        t.tick()
        t.finishEarly()
        assertFalse(t.running)
        assertEquals(0L, t.remaining)
        assertTrue(t.doneEarly)
        assertTrue("Ensin on yhä kesken kuittaamatta (valmis-kortti näkyy)", t.inProgress)
        assertEquals("Ei äänimerkkiä", 0, port.completed)
        assertFalse(port.isRunning())
        // Lisäaikaa ei voi enää antaa kuitattuun, mutta Siirry seuraavaan nollaa tilan.
        assertFalse(t.addMoreTime(120_000L))
        t.finishActivity()
        assertFalse(t.doneEarly)
    }

    @Test
    fun `ilman Ensin-toimintoa ei tehda mitaan`() {
        val t = FlowTimer(FakeTimer())
        t.setMinutes(5)
        t.finishEarly()
        assertFalse(t.doneEarly)
        assertEquals(300_000L, t.remaining)
    }
}
