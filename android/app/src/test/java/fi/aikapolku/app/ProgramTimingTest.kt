package fi.aikapolku.app

import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.TimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-23: päiväohjelman aikatavat ja kellonaikojen laskenta. Puhdas JVM-testi. */
class ProgramTimingTest {

    private fun step(id: String, minutes: Int = 10, mode: StepMode = StepMode.MANUAL, clock: String = "08:00", fixed: Boolean = false) =
        ProgramStep(id = id, name = "T$id", photo = "", description = "", mode = mode,
            minutes = minutes, clock = clock, fixedClock = fixed)

    private fun program(mode: TimeMode, vararg steps: ProgramStep, start: String = "07:30") =
        DayProgram("p", "Ohjelma", steps.toList(), timeMode = mode, startClock = start)

    @Test
    fun `kellonajan luku ja muotoilu`() {
        assertEquals(450, ProgramTiming.parse("07:30"))
        assertEquals(450, ProgramTiming.parse("7.30"))
        assertNull(ProgramTiming.parse("24:00"))
        assertNull(ProgramTiming.parse("klo"))
        assertEquals("07:05", ProgramTiming.format(425))
        assertEquals("00:10", ProgramTiming.format(1450))
    }

    @Test
    fun `kellonajat lasketaan aloitusajasta ja kestoista`() {
        val p = program(TimeMode.CLOCK, step("a", 15), step("b", 5), step("c", 10))
        val slots = ProgramTiming.schedule(p.startClock, p.steps).map { ProgramTiming.format(it.start) }
        assertEquals(listOf("07:30", "07:45", "07:50"), slots)
        assertEquals("08:00", ProgramTiming.format(ProgramTiming.endOf(p.startClock, p.steps)!!))
    }

    @Test
    fun `kiintea aika siirtaa seuraavat ajat`() {
        val p = program(TimeMode.CLOCK,
            step("a", 30), step("b", 30, clock = "11:00", fixed = true), step("c", 20))
        val slots = ProgramTiming.schedule(p.startClock, p.steps)
        assertEquals(listOf("07:30", "11:00", "11:30"), slots.map { ProgramTiming.format(it.start) })
        assertFalse(slots.any { it.overlap })
    }

    @Test
    fun `liian aikainen kiintea aika merkitaan paallekkaiseksi`() {
        val p = program(TimeMode.CLOCK, step("a", 60), step("b", 10, clock = "08:00", fixed = true))
        val slot = ProgramTiming.schedule(p.startClock, p.steps)[1]
        assertTrue(slot.overlap)
        assertEquals("08:30", ProgramTiming.format(slot.previousEnd))
    }

    @Test
    fun `ensimmainen toiminto alkaa aina aloitusajasta`() {
        val p = program(TimeMode.CLOCK, step("a", 10, clock = "12:00", fixed = true))
        assertEquals("07:30", ProgramTiming.format(ProgramTiming.schedule(p.startClock, p.steps)[0].start))
    }

    @Test
    fun `tallennus kirjoittaa kellonajat toimintojen kenttiin`() {
        val saved = ProgramTiming.apply(program(TimeMode.CLOCK, step("a", 15), step("b", 5)))
        assertEquals(listOf(StepMode.CLOCK, StepMode.CLOCK), saved.steps.map { it.mode })
        assertEquals(listOf("07:30", "07:45"), saved.steps.map { it.clock })
    }

    @Test
    fun `tallennus kestoilla sailyttaa Ei aikaa -valinnan`() {
        val saved = ProgramTiming.apply(program(TimeMode.DURATION, step("a", 15, StepMode.DURATION), step("b", mode = StepMode.MANUAL)))
        assertEquals(listOf(StepMode.DURATION, StepMode.MANUAL), saved.steps.map { it.mode })
    }

    @Test
    fun `omaan tahtiin poistaa ajat`() {
        val saved = ProgramTiming.apply(program(TimeMode.FREE, step("a", mode = StepMode.CLOCK, fixed = true)))
        assertEquals(StepMode.MANUAL, saved.steps[0].mode)
        assertFalse(saved.steps[0].fixedClock)
    }

    @Test
    fun `vanhan ohjelman aikatapa paatellaan ja kellonajat sailyvat`() {
        val old = DayProgram("p", "Vanha", listOf(
            step("a", 20, StepMode.CLOCK, "09:00"), step("b", 10, StepMode.CLOCK, "13:15")
        ))
        val inferred = ProgramTiming.infer(old)
        assertEquals(TimeMode.CLOCK, inferred.timeMode)
        assertEquals("09:00", inferred.startClock)
        assertEquals(listOf("09:00", "13:15"), ProgramTiming.apply(inferred).steps.map { it.clock })

        assertEquals(TimeMode.DURATION,
            ProgramTiming.infer(DayProgram("p", "x", listOf(step("a", mode = StepMode.DURATION)))).timeMode)
        assertEquals(TimeMode.FREE,
            ProgramTiming.infer(DayProgram("p", "x", listOf(step("a")))).timeMode)
    }

    @Test
    fun `valmis pohja avautuu kestoina`() {
        val template = ProgramTiming.infer(RoutineTemplates.create(RoutineTemplates.all.first()) { "T$it" })
        assertEquals(TimeMode.DURATION, template.timeMode)
        // Lähtö on pohjassa ilman aikaa ja pysyy sellaisena.
        assertEquals(StepMode.MANUAL, template.steps.last().mode)
    }

    @Test
    fun `kestoihin vaihdettaessa jokainen toiminto saa ajastimen`() {
        val switched = ProgramTiming.switchMode(program(TimeMode.FREE, step("a"), step("b")), TimeMode.DURATION)
        assertEquals(listOf(StepMode.DURATION, StepMode.DURATION), switched.steps.map { it.mode })
    }

    private val partNames: (ProgramTiming.DayPart) -> String = {
        when (it) {
            ProgramTiming.DayPart.MORNING -> "Aamurutiini"
            ProgramTiming.DayPart.DAY -> "Päiväohjelma"
            ProgramTiming.DayPart.EVENING -> "Iltarutiini"
        }
    }

    @Test
    fun `nimiehdotus ei toista olemassa olevaa nimea`() {
        val morning = program(TimeMode.CLOCK, step("a"), start = "07:00")
        assertEquals("Aamurutiini", ProgramTiming.suggestName(morning, emptyList(), partNames))
        assertEquals("Aamurutiini 2", ProgramTiming.suggestName(morning, listOf("aamurutiini"), partNames))
        assertEquals("Iltarutiini", ProgramTiming.suggestName(program(TimeMode.CLOCK, step("a"), start = "19:00"), emptyList(), partNames))
        assertEquals("Päiväohjelma", ProgramTiming.suggestName(program(TimeMode.FREE, step("a")), emptyList(), partNames))
    }
}
