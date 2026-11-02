package fi.aikapolku.app

import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Viikko-ohjelman päättely: päivän tapahtumat, vuoroviikot ja "nyt"-tapahtuma. */
class WeekScheduleTest {
    // 2.11.2026 on maanantai.
    private val monday = LocalDate.of(2026, 11, 2)

    private fun ev(id: String, day: Int, time: String, week: Int = 0, programId: String = "p") =
        WeekEvent(id, day, week, time, EventKind.PROGRAM, programId = programId)

    @Test
    fun `paivan tapahtumat kellonajan mukaan`() {
        val plan = WeekPlan(listOf(ev("b", 1, "12:00"), ev("a", 1, "07:30"), ev("c", 2, "08:00")))
        assertEquals(listOf("a", "b"), WeekSchedule.eventsFor(plan, monday).map { it.id })
        assertEquals(listOf("c"), WeekSchedule.eventsFor(plan, monday.plusDays(1)).map { it.id })
        assertEquals(emptyList<WeekEvent>(), WeekSchedule.eventsFor(plan, monday.plusDays(6)))
    }

    @Test
    fun `ilman vuoroviikkoja B-viikon tapahtumia ei naytetaan`() {
        val plan = WeekPlan(listOf(ev("a", 1, "08:00", week = 0), ev("b", 1, "09:00", week = 1)))
        assertEquals(listOf("a"), WeekSchedule.eventsFor(plan, monday).map { it.id })
        assertEquals(listOf("a"), WeekSchedule.eventsFor(plan, monday.plusWeeks(1)).map { it.id })
    }

    @Test
    fun `vuoroviikot vuorottelevat A ja B`() {
        val plan = WeekSchedule.withCurrentWeek(
            WeekPlan(listOf(ev("a", 3, "08:00", week = 0), ev("b", 3, "08:00", week = 1)), alternating = true),
            today = monday.plusDays(3), week = WeekSchedule.WEEK_A
        )
        val wednesday = monday.plusDays(2)
        assertEquals(listOf("a"), WeekSchedule.eventsFor(plan, wednesday).map { it.id })
        assertEquals(listOf("b"), WeekSchedule.eventsFor(plan, wednesday.plusWeeks(1)).map { it.id })
        assertEquals(listOf("a"), WeekSchedule.eventsFor(plan, wednesday.plusWeeks(2)).map { it.id })
        assertEquals(listOf("b"), WeekSchedule.eventsFor(plan, wednesday.minusWeeks(1)).map { it.id })
    }

    @Test
    fun `taman viikon voi asettaa B-viikoksi`() {
        val plan = WeekSchedule.withCurrentWeek(WeekPlan(alternating = true), monday.plusDays(6), WeekSchedule.WEEK_B)
        assertEquals(WeekSchedule.WEEK_B, WeekSchedule.weekOf(plan, monday))
        assertEquals(WeekSchedule.WEEK_B, WeekSchedule.weekOf(plan, monday.plusDays(6)))
        assertEquals(WeekSchedule.WEEK_A, WeekSchedule.weekOf(plan, monday.plusDays(7)))
    }

    @Test
    fun `nyt on viimeisin alkanut tapahtuma ja ennen ensimmaista ensimmainen`() {
        val events = listOf(ev("a", 1, "07:00"), ev("b", 1, "08:00"), ev("c", 1, "12:00"))
        assertEquals(0, WeekSchedule.currentIndex(events, LocalTime.of(6, 0)))
        assertEquals(0, WeekSchedule.currentIndex(events, LocalTime.of(7, 0)))
        assertEquals(1, WeekSchedule.currentIndex(events, LocalTime.of(11, 59)))
        assertEquals(2, WeekSchedule.currentIndex(events, LocalTime.of(23, 0)))
        assertEquals(-1, WeekSchedule.currentIndex(emptyList(), LocalTime.NOON))
    }

    @Test
    fun `kellonajan muotoilu ja luku`() {
        assertEquals("07:05", WeekSchedule.formatTime(7, 5))
        assertEquals(7 * 60 + 5, WeekSchedule.minutesOf("07:05"))
        assertEquals(0, WeekSchedule.minutesOf("rikki"))
        assertEquals(23 * 60 + 59, WeekSchedule.minutesOf("99:99"))
    }

    @Test
    fun `poistetun ohjelman tapahtumat poistuvat mutta kortit sailyvat`() {
        val card = WeekEvent("k", 1, 0, "12:00", EventKind.CARD, cardName = "Lääkkeet")
        val plan = WeekPlan(listOf(ev("a", 1, "08:00", programId = "jaljella"), ev("b", 1, "09:00", programId = "poistettu"), card))
        assertEquals(
            listOf("a", "k"),
            WeekSchedule.withoutMissingPrograms(plan, setOf("jaljella")).events.map { it.id }
        )
    }

    @Test
    fun `tehty tapahtuma ei ole enaa korostettu vaan seuraava tekematon on`() {
        val events = listOf(ev("a", 1, "07:00"), ev("b", 1, "08:00"), ev("c", 1, "12:00"))
        // Klo 8.10: b on alkanut ja tekemättä.
        assertEquals(1, WeekSchedule.currentIndex(events, LocalTime.of(8, 10), done = setOf("a")))
        // b tehty: korostus siirtyy tulevaan c:hen.
        assertEquals(2, WeekSchedule.currentIndex(events, LocalTime.of(8, 10), done = setOf("b")))
        // Ennen ensimmäistä, ensimmäinen jo tehty: korostetaan seuraava.
        assertEquals(1, WeekSchedule.currentIndex(events, LocalTime.of(6, 0), done = setOf("a")))
        // Kaikki tehty: ei korostusta.
        assertEquals(-1, WeekSchedule.currentIndex(events, LocalTime.of(13, 0), done = setOf("a", "b", "c")))
        // Päivä ohi ja viimeinen tehty: ei korostusta.
        assertEquals(-1, WeekSchedule.currentIndex(events, LocalTime.of(23, 0), done = setOf("c")))
    }

    @Test
    fun `tiivis lista on nyt ja enintaan kaksi seuraavaa tekematonta`() {
        val events = (1..6).map { ev("e$it", 1, "%02d:00".format(it + 6)) }
        assertEquals(listOf(1, 2, 3), WeekSchedule.compactIndices(events, current = 1, done = emptySet()))
        assertEquals(listOf(1, 3, 5), WeekSchedule.compactIndices(events, current = 1, done = setOf("e3", "e5")))
        assertEquals(listOf(5), WeekSchedule.compactIndices(events, current = 5, done = emptySet()))
        assertEquals(emptyList<Int>(), WeekSchedule.compactIndices(events, current = -1, done = emptySet()))
    }
}
