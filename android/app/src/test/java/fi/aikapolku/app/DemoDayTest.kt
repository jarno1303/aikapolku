package fi.aikapolku.app

import fi.aikapolku.app.data.DemoDay
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Esittelypäivän sisältö (sama kuin puhelimen automaattitestissä). Puhdas JVM-testi. */
class DemoDayTest {
    private val texts: (Int) -> String = { "t$it" }
    // 2.11.2026 maanantai klo 18.04.
    private val now = LocalDateTime.of(2026, 11, 2, 18, 4)

    @Test
    fun `ohjelmassa kolme tehtavaa ja toinen kestaa minuutin`() {
        val p = DemoDay.program(texts)
        assertEquals(3, p.steps.size)
        assertEquals(StepMode.DURATION, p.steps[1].mode)
        assertEquals(1, p.steps[1].minutes)
        assertTrue(p.steps.all { it.subSteps.isEmpty() })
    }

    @Test
    fun `ohjelma alkoi minuutti sitten ja myohempi tapahtuma on tulossa`() {
        val (plan, result) = DemoDay.plan(WeekPlan(), now, texts)
        val today = WeekSchedule.eventsFor(plan, now.toLocalDate())
        assertEquals(listOf("18:03", "18:24"), today.map { it.time })
        assertEquals(result.programEventId, today[0].id)
        assertEquals(EventKind.PROGRAM, today[0].kind)
        // Nyt korostuu ohjelma (alkanut, tekemättä).
        assertEquals(0, WeekSchedule.currentIndex(today, now.toLocalTime()))
    }

    @Test
    fun `uusi esittely korvaa aiemman eika koske muihin tapahtumiin`() {
        val other = WeekEvent("oma", 1, 0, "08:00", EventKind.CARD, cardName = "Oma")
        val (first, _) = DemoDay.plan(WeekPlan(listOf(other)), now, texts)
        val (second, _) = DemoDay.plan(first, now.plusMinutes(10), texts)
        assertEquals(3, second.events.size)
        assertTrue(second.events.contains(other))
    }

    @Test
    fun `myohaan illalla ajat pysyvat samassa paivassa`() {
        val (plan, _) = DemoDay.plan(WeekPlan(), LocalDateTime.of(2026, 11, 2, 23, 58), texts)
        assertEquals(listOf("23:57", "23:59"), plan.events.map { it.time })
    }
}
