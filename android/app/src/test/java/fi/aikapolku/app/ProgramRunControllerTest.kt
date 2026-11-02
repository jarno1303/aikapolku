package fi.aikapolku.app

import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.programs.run.ProgramRunController
import fi.aikapolku.app.programs.run.RunProgress
import fi.aikapolku.app.timer.Tick
import fi.aikapolku.app.timer.TimerPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.programs.run.DoneGate

/** Päiväohjelman suorituksen säännöt (ProgramRunController). Puhdas JVM-testi väärennetyllä ajastimella. */
class ProgramRunControllerTest {

    private class FakeTimer : TimerPort {
        var clock = 1_000L
        var allowedNow = true
        var permissionRequests = 0
        var runningUntil: Long? = null
        var stoppedAt = -1L
        var completed = false
        override fun now() = clock
        override fun allowed() = allowedNow
        override fun requestPermissions() { permissionRequests++ }
        override fun start(durationMs: Long, title: String): Long { runningUntil = clock + durationMs; completed = false; return runningUntil!! }
        override fun stop(remainingMs: Long) { runningUntil = null; stoppedAt = remainingMs }
        override fun remaining(fallbackMs: Long) = runningUntil?.let { (it - clock).coerceAtLeast(0L) } ?: fallbackMs
        override fun isRunning() = runningUntil != null
        override fun complete(): Boolean { val first = !completed && runningUntil != null; completed = true; runningUntil = null; return first }
    }

    private class FakeProgress : RunProgress {
        override var programId = ""
        override var stepIndex = 0
        override var subStepIndex = 0
        override fun save(programId: String, stepIndex: Int, subStepIndex: Int) {
            this.programId = programId; this.stepIndex = stepIndex; this.subStepIndex = subStepIndex
        }
        override fun clear() { programId = ""; stepIndex = 0; subStepIndex = 0 }
    }

    private val timer = FakeTimer()
    private val progress = FakeProgress()
    private val voices = mutableListOf<String>()
    private val run = ProgramRunController(timer, progress) { voices += it }

    private fun step(id: String, mode: StepMode = StepMode.MANUAL, minutes: Int = 5, sub: List<String> = emptyList(), voice: String = "") =
        ProgramStep(id, "Tehtävä $id", "", "", mode = mode, minutes = minutes, subSteps = sub,
            voicePath = voice, autoPlayVoice = voice.isNotEmpty())

    private val program = DayProgram("p", "Aamu", listOf(
        step("a", sub = listOf("Sukat", "Housut")),
        step("b", StepMode.DURATION, minutes = 10, voice = "/b.m4a"),
        step("c", voice = "/c.m4a")
    ))

    @Test
    fun `aloitus vie ensimmaiseen tehtavaan ja tallentaa kohdan`() {
        run.start(program)
        assertEquals("a", run.currentStep?.id)
        assertEquals(0, run.subStepIndex)
        assertEquals("p", progress.programId)
        assertFalse(run.running)
    }

    @Test
    fun `valmis etenee osavaiheittain ja sitten seuraavaan tehtavaan`() {
        run.start(program)
        assertFalse(run.next())
        assertEquals(1, run.subStepIndex)
        assertEquals(0, run.stepIndex)
        assertFalse(run.next())
        assertEquals("b", run.currentStep?.id)
        assertEquals(0, run.subStepIndex)
        assertEquals(1, progress.stepIndex)
    }

    @Test
    fun `viimeinen valmis paattaa ohjelman ja tyhjentaa kohdan`() {
        run.start(program)
        repeat(3) { run.next() }
        assertTrue(run.next())
        assertEquals("", progress.programId)
    }

    @Test
    fun `kestotehtavan aika alkaa tehtavan kestosta ja aani soi vasta kaynnistettaessa`() {
        run.start(program)
        run.next(); run.next()
        assertEquals(10 * 60_000L, run.remaining)
        assertTrue(voices.isEmpty())
        run.startTimer()
        assertTrue(run.running)
        assertEquals(listOf("/b.m4a"), voices)
        run.pause(); run.startTimer()
        assertEquals("puheviesti ei toistu uudelleen", 1, voices.size)
    }

    @Test
    fun `ilman aikaa olevan tehtavan aani soi heti`() {
        run.start(program)
        repeat(3) { run.next() }
        assertEquals("c", run.currentStep?.id)
        assertEquals(listOf("/c.m4a"), voices)
    }

    @Test
    fun `tauko sailyttaa jaljella olevan ajan`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        timer.clock += 60_000L
        run.pause()
        assertFalse(run.running)
        assertEquals(9 * 60_000L, run.remaining)
        assertEquals(9 * 60_000L, timer.stoppedAt)
    }

    @Test
    fun `apupyynto pysayttaa ajan ja jatka kaynnistaa sen uudelleen`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        run.requestSupport(SupportKind.HELP)
        assertEquals(SupportKind.HELP, run.support)
        assertFalse(run.running)
        assertTrue(run.resumeAfterSupport)
        run.continueAfterSupport()
        assertNull(run.support)
        assertTrue(run.running)
    }

    @Test
    fun `pysaytetty aika ei kaynnisty taukopyynnon jalkeen`() {
        run.start(program); run.next(); run.next()
        run.requestSupport(SupportKind.BREAK)
        run.continueAfterSupport()
        assertFalse(run.running)
    }

    @Test
    fun `viestin sulkeminen ei kaynnista aikaa`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        run.requestSupport(SupportKind.BREAK)
        run.closeSupport()
        assertNull(run.support)
        assertFalse(run.running)
    }

    @Test
    fun `ilman lupaa ajastin ei kaynnisty vaan lupaa pyydetaan`() {
        timer.allowedNow = false
        run.start(program); run.next(); run.next()
        run.startTimer()
        assertFalse(run.running)
        assertEquals(1, timer.permissionRequests)
    }

    @Test
    fun `aika paattyy tasan kerran ja aanimerkki soi kerran`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        timer.clock += 5 * 60_000L
        assertEquals(Tick.RUNNING, run.tick())
        timer.clock += 5 * 60_000L
        assertEquals(Tick.ENDED, run.tick())
        assertFalse(run.running)
        assertEquals(0L, run.remaining)
    }

    @Test
    fun `lisaaika lisaa viisi minuuttia kaynnissa olevaan aikaan`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        timer.clock += 60_000L
        run.extend(5 * 60_000L, "Ajastin")
        assertEquals(14 * 60_000L, run.remaining)
        assertTrue(run.running)
    }

    @Test
    fun `lopetus pysayttaa ajan ja unohtaa kohdan`() {
        run.start(program); run.next(); run.next()
        run.startTimer()
        run.requestSupport(SupportKind.HELP)
        run.end()
        assertFalse(run.running)
        assertNull(run.support)
        assertEquals("", progress.programId)
    }

    @Test
    fun `keskeytynyt ohjelma jatkuu tallennetusta kohdasta`() {
        progress.save("p", 2, 0)
        run.resume(program)
        assertEquals("c", run.currentStep?.id)
    }

    @Test
    fun `tyhjaa ohjelmaa ei kaynnisteta`() {
        run.start(DayProgram("x", "Tyhjä", emptyList()))
        assertFalse(run.isActive)
    }

    // --- Ohitus, vahinkonaputusten esto ja Valmis-asetus ---

    @Test
    fun `ohita ohittaa koko tehtavan osavaiheineen ja kirjaa sen`() {
        run.start(program)
        assertFalse(run.skip())
        assertEquals("b", run.currentStep?.id)
        assertEquals(setOf(0), run.skipped)
        run.next()
        assertEquals(setOf(0), run.skipped)
    }

    @Test
    fun `uusi ohjelma tyhjentaa ohitetut`() {
        run.start(program)
        run.skip()
        run.start(program)
        assertEquals(emptySet<Int>(), run.skipped)
    }

    @Test
    fun `valmis ei reagoi heti uuden vaiheen alussa`() {
        run.start(program)
        assertTrue(run.tapGuardActive())
        timer.clock += ProgramRunController.TAP_GUARD_MS
        assertFalse(run.tapGuardActive())
        run.next()
        assertTrue("osavaiheen vaihtuminen käynnistää eston uudelleen", run.tapGuardActive())
    }

    private fun atDurationStep() {
        run.start(program)
        run.next(); run.next() // a: kaksi osavaihetta -> b (10 min)
        assertEquals("b", run.currentStep?.id)
    }

    @Test
    fun `valmis-asetus aina sallii heti`() {
        atDurationStep()
        run.startTimer()
        assertEquals(DoneGate.ALLOW, run.doneGate(RunPreferences.DONE_ALWAYS, caregiverOpen = false))
    }

    @Test
    fun `kysy ensin kun aikaa on jaljella`() {
        atDurationStep()
        run.startTimer()
        assertEquals(DoneGate.CONFIRM, run.doneGate(RunPreferences.DONE_CONFIRM, caregiverOpen = false))
        timer.clock += 10 * 60_000L
        assertEquals(DoneGate.ALLOW, run.doneGate(RunPreferences.DONE_CONFIRM, caregiverOpen = false))
    }

    @Test
    fun `vasta ajan jalkeen odottaa ja ohjaaja voi ohittaa kysyen`() {
        atDurationStep()
        assertEquals(DoneGate.START_TIMER, run.doneGate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false))
        run.startTimer()
        assertEquals(DoneGate.WAIT, run.doneGate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false))
        assertEquals(DoneGate.CONFIRM, run.doneGate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = true))
        timer.clock += 10 * 60_000L
        assertEquals(DoneGate.ALLOW, run.doneGate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false))
    }

    @Test
    fun `rajoitus ei koske tehtavia ilman aikaa`() {
        run.start(program)
        assertEquals(DoneGate.ALLOW, run.doneGate(RunPreferences.DONE_AFTER_TIME, caregiverOpen = false))
    }

    // --- Päivän muutokset: peruttu vaihe ohitetaan, korvattu vaihdetaan ---

    private fun cancelled(p: DayProgram, vararg ids: String) =
        p.copy(steps = p.steps.map { if (it.id in ids) it.copy(changedToday = true, cancelledToday = true) else it })

    @Test
    fun `peruttu ensimmainen vaihe ohitetaan aloituksessa`() {
        run.start(cancelled(program, "a"))
        assertEquals("b", run.currentStep?.id)
        assertEquals(0, run.activeIndex)
        assertEquals(2, run.activeCount)
        assertEquals(1, progress.stepIndex)
        assertTrue(0 in run.skipped)
    }

    @Test
    fun `peruttu valivaihe ohitetaan ja seuraava nayttaa oikean`() {
        run.start(cancelled(program, "b"))
        assertEquals("c", run.nextStep?.id)
        run.next(); run.next() // osavaiheet
        assertEquals("c", run.currentStep?.id)
        assertEquals(1, run.activeIndex)
    }

    @Test
    fun `viimeinen peruttu paattaa ohjelman`() {
        run.start(cancelled(program, "c"))
        run.next(); run.next()
        assertEquals("b", run.currentStep?.id)
        assertTrue(run.next())
    }

    @Test
    fun `jatkettaessa peruttu kohta ohitetaan`() {
        progress.save("p", 1, 0)
        run.resume(cancelled(program, "b"))
        assertEquals("c", run.currentStep?.id)
    }

    @Test
    fun `kesken suorituksen korvattu vaihe vaihtuu ja osavaiheet alkavat alusta`() {
        run.start(program)
        run.next()
        assertEquals(1, run.subStepIndex)
        run.replaceCurrent(program.steps[0].copy(name = "Lepo", subSteps = emptyList(), changedToday = true, changedFrom = "Tehtävä a"))
        assertEquals("Lepo", run.currentStep?.name)
        assertEquals(0, run.subStepIndex)
        assertEquals("a", run.currentStep?.id)
    }
}
