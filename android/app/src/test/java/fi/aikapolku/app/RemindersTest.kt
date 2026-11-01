package fi.aikapolku.app

import fi.aikapolku.app.data.AckLog
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ilmoitusten ajoitus kaikista profiileista ja kuittausten säilytysaika. */
class RemindersTest {
    // 2.11.2026 on maanantai.
    private val monday = LocalDate.of(2026, 11, 2)

    private fun card(id: String, day: Int, time: String, notify: Boolean = true, before: Int = 0) =
        WeekEvent(id, day, 0, time, EventKind.CARD, cardName = id, notify = notify, notifyBefore = before)

    @Test
    fun `seuraava ilmoitus on lahin tuleva ja ilman ilmoitusta olevat ohitetaan`() {
        val plans = mapOf(
            "mikko" to WeekPlan(listOf(card("aamu", 1, "08:00"), card("hiljainen", 1, "08:30", notify = false))),
            "sanna" to WeekPlan(listOf(card("laakkeet", 1, "09:00", before = 10)))
        )
        assertEquals(monday.atTime(8, 0), WeekSchedule.nextReminderAt(plans, monday.atTime(7, 0)))
        assertEquals(monday.atTime(8, 50), WeekSchedule.nextReminderAt(plans, monday.atTime(8, 0)))
        // Viikon viimeisen jälkeen seuraava on ensi maanantaina.
        assertEquals(monday.plusWeeks(1).atTime(8, 0), WeekSchedule.nextReminderAt(plans, monday.atTime(9, 0)))
    }

    @Test
    fun `ilman ilmoituksia ei ajasteta mitaan`() {
        assertNull(WeekSchedule.nextReminderAt(mapOf("p" to WeekPlan(listOf(card("a", 1, "08:00", notify = false)))), monday.atTime(7, 0)))
        assertNull(WeekSchedule.nextReminderAt(emptyMap(), monday.atTime(7, 0)))
    }

    @Test
    fun `saman hetken kaikki ilmoitukset naytetaan`() {
        val plans = mapOf(
            "mikko" to WeekPlan(listOf(card("a", 1, "08:00"))),
            "sanna" to WeekPlan(listOf(card("b", 1, "08:10", before = 10), card("c", 1, "12:00")))
        )
        val due = WeekSchedule.dueReminders(plans, monday.atTime(8, 0, 30))
        assertEquals(setOf("mikko" to "a", "sanna" to "b"), due.map { it.profileId to it.event.id }.toSet())
    }

    @Test
    fun `keskiyon jalkeisen tapahtuman ennakkoilmoitus tulee edellisena iltana`() {
        val plans = mapOf("p" to WeekPlan(listOf(card("yo", 2, "00:05", before = 15))))
        val at = monday.atTime(23, 50)
        assertEquals(at, WeekSchedule.nextReminderAt(plans, monday.atTime(20, 0)))
        val due = WeekSchedule.dueReminders(plans, at)
        assertEquals(listOf("yo"), due.map { it.event.id })
        assertEquals(monday.plusDays(1), due.first().date)
    }

    @Test
    fun `vuoroviikoilla ilmoitus vain oikealla viikolla`() {
        val plan = WeekSchedule.withCurrentWeek(
            WeekPlan(listOf(card("b-viikko", 1, "08:00").copy(week = 1)), alternating = true),
            monday, WeekSchedule.WEEK_A
        )
        assertEquals(
            monday.plusWeeks(1).atTime(8, 0),
            WeekSchedule.nextReminderAt(mapOf("p" to plan), monday.atTime(7, 0))
        )
    }

    @Test
    fun `kuittaukset sailyvat eiliselta ja talta paivalta`() {
        val keys = listOf(
            AckLog.key(monday, "a"),
            AckLog.key(monday.minusDays(1), "b"),
            AckLog.key(monday.minusDays(2), "c"),
            "rikki"
        )
        assertEquals(listOf(AckLog.key(monday.minusDays(2), "c"), "rikki"), AckLog.expired(keys, monday))
    }

    @Test
    fun `ilmoitushetki on minuutin tarkkuudella`() {
        val plans = mapOf("p" to WeekPlan(listOf(card("a", 1, "08:00"))))
        // Hälytys voi laueta sekunteja myöhässä; sama minuutti ei ajastu uudelleen.
        assertEquals(monday.plusWeeks(1).atTime(8, 0), WeekSchedule.nextReminderAt(plans, LocalDateTime.of(monday, java.time.LocalTime.of(8, 0, 2))))
    }
}
