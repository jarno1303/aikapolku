package fi.aikapolku.app

import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.firstthen.FirstThenCards
import fi.aikapolku.app.firstthen.FlowTimer
import fi.aikapolku.app.timer.Tick
import fi.aikapolku.app.timer.TimerPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensin–Sitten-kulun säännöt (FirstThenCards, FlowTimer). Puhdas JVM-testi. */
class FirstThenFlowTest {

    private class FakeTimer : TimerPort {
        var clock = 0L
        var until: Long? = null
        var allowedNow = true
        var lastTitle = ""
        override fun now() = clock
        override fun allowed() = allowedNow
        override fun requestPermissions() {}
        override fun start(durationMs: Long, title: String): Long { lastTitle = title; until = clock + durationMs; return until!! }
        override fun stop(remainingMs: Long) { until = null }
        override fun remaining(fallbackMs: Long) = until?.let { (it - clock).coerceAtLeast(0L) } ?: fallbackMs
        override fun isRunning() = until != null
        override fun complete(): Boolean { val first = until != null; until = null; return first }
    }

    private val selection = FirstThenSelection(
        firstName = "Askartelu", nextName = "Ruokailu", firstPhoto = "/a", nextPhoto = "/b",
        nextDescription = "Kala", nextVoice = "/r.m4a", nextVoiceAuto = true
    )

    @Test
    fun `sitten-kortista tulee nykyinen toiminto`() {
        val cards = FirstThenCards(selection)
        assertTrue(cards.promoteNext())
        val s = cards.selection
        assertEquals("Ruokailu", s.firstName)
        assertEquals("/b", s.firstPhoto)
        assertEquals("Kala", s.firstDescription)
        assertEquals("/r.m4a", s.firstVoice)
        assertTrue(s.firstVoiceAuto)
    }

    @Test
    fun `ilman sitten-korttia kulku paattyy`() {
        val cards = FirstThenCards(selection)
        cards.clearNext()
        assertFalse(cards.hasNext)
        assertFalse(cards.promoteNext())
        assertEquals("Askartelu", cards.selection.firstName)
    }

    @Test
    fun `kulun paatyttya alkuperainen pari palaa`() {
        // Puhelintesti 2.11.2026: lopussa molemmissa korteissa näkyi "Ruokailu".
        val cards = FirstThenCards(selection)
        assertTrue(cards.promoteNext())
        assertEquals("Ruokailu", cards.selection.firstName)
        cards.finishFlow()
        assertEquals(selection, cards.selection)
        assertEquals(null, cards.original)
    }

    @Test
    fun `lisatty vaihe ei muuta alkuperaista paria`() {
        val cards = FirstThenCards(selection)
        cards.promoteNext()
        cards.clearNext()
        cards.update { it.copy(nextName = "Lepo") }
        assertTrue(cards.promoteNext())
        assertEquals("Lepo", cards.selection.firstName)
        cards.finishFlow()
        assertEquals("Askartelu", cards.selection.firstName)
        assertEquals("Ruokailu", cards.selection.nextName)
    }

    @Test
    fun `ilman siirtoa lopetus ei muuta valintaa`() {
        val cards = FirstThenCards(selection)
        cards.update { it.copy(firstName = "Lepo") }
        cards.finishFlow()
        assertEquals("Lepo", cards.selection.firstName)
    }

    @Test
    fun `kesken oleva kulku sailyy tilan tallennuksessa`() {
        val cards = FirstThenCards(selection)
        cards.promoteNext()
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        val saved = with(FirstThenCards.saver) { scope.save(cards) }!!
        val restored = FirstThenCards.saver.restore(saved)!!
        assertEquals(cards.selection, restored.selection)
        assertEquals(selection, restored.original)
        restored.finishFlow()
        assertEquals(selection, restored.selection)
    }

    @Test
    fun `vanhan version Valitse toiminto -teksti tulkitaan valitsemattomaksi`() {
        val cards = FirstThenCards(selection.copy(nextName = "Valitse toiminto"))
        assertFalse(cards.hasNext)
        assertEquals("", cards.selection.nextName)
    }

    @Test
    fun `ajastimen aika asetetaan ja nollaus palauttaa sen`() {
        val t = FlowTimer(FakeTimer())
        t.setMinutes(10)
        assertEquals(600_000L, t.remaining)
        t.startPause("Ajastin") {}
        assertTrue(t.running)
        t.reset()
        assertFalse(t.running)
        assertEquals(600_000L, t.remaining)
    }

    @Test
    fun `ensin-toiminnon puheviesti soi kerran ajastuksen alkaessa`() {
        val port = FakeTimer()
        val t = FlowTimer(port)
        var voices = 0
        t.prepareForActivity("Askartelu", 5)
        t.startPause("Ajastin") { voices++ }
        assertEquals("Askartelu", port.lastTitle)
        t.startPause("Ajastin") { voices++ } // tauko
        t.startPause("Ajastin") { voices++ } // jatka
        assertEquals(1, voices)
    }

    @Test
    fun `pelkka ajastin ei soita puheviestia ja kayttaa yleista otsikkoa`() {
        val port = FakeTimer()
        val t = FlowTimer(port)
        var voices = 0
        t.setMinutes(3)
        t.startPause("Ajastin") { voices++ }
        assertEquals(0, voices)
        assertEquals("Ajastin", port.lastTitle)
    }

    @Test
    fun `tauko sailyttaa jaljella olevan ajan`() {
        val port = FakeTimer()
        val t = FlowTimer(port)
        t.prepareForActivity("Askartelu", 5)
        t.startPause("Ajastin") {}
        port.clock += 60_000L
        t.stopKeepingRemaining()
        assertFalse(t.running)
        assertEquals(240_000L, t.remaining)
    }

    @Test
    fun `paattynyt aika kaynnistyy uudelleen alkuperaisella ajalla`() {
        val port = FakeTimer()
        val t = FlowTimer(port)
        t.setMinutes(2)
        t.startPause("Ajastin") {}
        port.clock += 120_000L
        assertEquals(Tick.ENDED, t.tick())
        assertEquals(0L, t.remaining)
        t.startPause("Ajastin") {}
        assertEquals(120_000L, t.remaining)
        assertTrue(t.running)
    }

    @Test
    fun `ensin-ajastus on kesken kunnes se kuitataan`() {
        val fake = FakeTimer()
        val t = FlowTimer(fake)
        t.prepareForActivity("Aamupala", 5)
        assertFalse("ei vielä aloitettu", t.inProgress)
        t.startPause("Ajastin") {}
        assertTrue("käynnissä", t.inProgress)
        fake.clock = 60_000L
        t.tick()
        t.startPause("Ajastin") {}
        assertFalse(t.running)
        assertTrue("tauolla", t.inProgress)
        t.finishActivity()
        assertFalse("kuitattu", t.inProgress)
    }

    @Test
    fun `yksinaan kaytetty ajastin ei ole kesken oleva ensin-ajastus`() {
        val t = FlowTimer(FakeTimer())
        t.setMinutes(3)
        t.startPause("Ajastin") {}
        assertFalse(t.inProgress)
    }

    @Test
    fun `rauhallinen nayttaa nelja osaa jotka poistuvat yksi kerrallaan`() {
        val d = 400_000L
        assertEquals(4, fi.aikapolku.app.timer.CalmProgress.segmentsLeft(d, d))
        assertEquals(4, fi.aikapolku.app.timer.CalmProgress.segmentsLeft(300_001L, d))
        assertEquals(3, fi.aikapolku.app.timer.CalmProgress.segmentsLeft(300_000L, d))
        assertEquals(1, fi.aikapolku.app.timer.CalmProgress.segmentsLeft(1L, d))
        assertEquals(0, fi.aikapolku.app.timer.CalmProgress.segmentsLeft(0L, d))
    }
}
