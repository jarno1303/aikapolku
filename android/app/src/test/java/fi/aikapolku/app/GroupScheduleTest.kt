package fi.aikapolku.app

import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupSchedule
import fi.aikapolku.app.data.MemberException
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ryhmän runko asiakkaan päivässä: jäsenyys, poissaolo, poikkeukset, muutokset ja muistutukset. Puhdas JVM-testi. */
class GroupScheduleTest {
    // 2026-11-02 on maanantai.
    private val monday = LocalDate.of(2026, 11, 2)

    private fun card(id: String, time: String, name: String, day: Int = 1, week: Int = 0, notify: Boolean = false) =
        WeekEvent(id, day, week, time, EventKind.CARD, cardName = name, cardPhoto = "builtin:x", notify = notify)

    private val group = Group(
        id = "tammi",
        name = "Päivätoiminta Tammi",
        members = listOf("mikko", "anna"),
        plan = WeekPlan(listOf(
            card("kokous", "09:00", "Aamukokous"),
            card("lounas", "11:00", "Lounas"),
            card("liikunta", "13:00", "Liikunta"),
            WeekEvent("ohjelma", 1, 0, "14:00", EventKind.PROGRAM, programId = "p")
        ))
    )
    private val ownMikko = WeekPlan(listOf(card("laake", "12:00", "Lääkkeet")))

    private fun ids(day: ClientDay, date: LocalDate = monday) = day.eventsFor(date).map { it.id }

    @Test
    fun `jasenen paivassa ovat ryhman kohdat ja omat kellonajan mukaan`() {
        val day = ClientDay(ownMikko, listOf(group), "mikko")
        assertEquals(
            listOf("grp:tammi:kokous", "grp:tammi:lounas", "laake", "grp:tammi:liikunta"),
            ids(day)
        )
        assertEquals("Lounas", day.eventsFor(monday)[1].cardName)
    }

    @Test
    fun `muu kuin jasen ei saa ryhman kohtia ja ilman ryhmia paiva on ennallaan`() {
        assertEquals(listOf("laake"), ids(ClientDay(ownMikko, listOf(group), "pekka")))
        assertEquals(listOf("laake"), ids(ClientDay(ownMikko, emptyList(), "mikko")))
        // Tiistaina ryhmällä ei ole runkoa.
        assertTrue(ClientDay(WeekPlan(), listOf(group), "mikko").eventsFor(monday.plusDays(1)).isEmpty())
    }

    @Test
    fun `poissa olevalle ei tule ryhman ohjelmaa, omat jaavat`() {
        val day = ClientDay(ownMikko, listOf(group), "mikko") { g, d -> g == "tammi" && d == monday }
        assertEquals(listOf("laake"), ids(day))
        // Seuraavalla viikolla taas paikalla.
        assertEquals(4, ids(day, monday.plusWeeks(1)).size)
    }

    @Test
    fun `toistuva poikkeus poistaa tai korvaa kohdan vain silta asiakkaalta`() {
        val withExceptions = group.copy(exceptions = listOf(
            MemberException("kokous", "mikko", ExceptionKind.SKIP),
            MemberException("liikunta", "mikko", ExceptionKind.REPLACE, "Fysioterapia", "builtin:fysio")
        ))
        val mikko = ClientDay(WeekPlan(), listOf(withExceptions), "mikko")
        val anna = ClientDay(WeekPlan(), listOf(withExceptions), "anna")
        assertEquals(listOf("grp:tammi:lounas", "grp:tammi:liikunta"), ids(mikko))
        val replaced = mikko.eventsFor(monday).last()
        assertEquals("Fysioterapia", replaced.cardName)
        assertEquals("13:00", replaced.time)
        assertEquals(listOf("Aamukokous", "Lounas", "Liikunta"), anna.eventsFor(monday).map { it.cardName })
    }

    @Test
    fun `rungossa vain kortit, paivaohjelmat ovat asiakaskohtaisia`() {
        val day = ClientDay(WeekPlan(), listOf(group), "anna")
        assertTrue(day.eventsFor(monday).none { it.kind == EventKind.PROGRAM })
        assertNull(day.event("grp:tammi:ohjelma"))
    }

    @Test
    fun `tapahtuma loytyy tunnuksella ja tunnukset kattavat ryhman kohdat`() {
        val day = ClientDay(ownMikko, listOf(group), "mikko")
        assertEquals("Lounas", day.event("grp:tammi:lounas")?.cardName)
        assertEquals("Lääkkeet", day.event("laake")?.cardName)
        assertNull(day.event("grp:muu:lounas"))
        assertNull(ClientDay(WeekPlan(), listOf(group), "pekka").event("grp:tammi:lounas"))
        assertTrue(day.eventIds().containsAll(setOf("laake", "grp:tammi:kokous", "grp:tammi:lounas")))
        assertEquals("tammi" to "lounas", GroupSchedule.parse("grp:tammi:lounas"))
        assertNull(GroupSchedule.parse("grp:tammi:"))
        assertNull(GroupSchedule.parse("lounas"))
    }

    @Test
    fun `ryhman vuoroviikot seuraavat ryhman omaa rytmia`() {
        val ab = group.copy(plan = WeekPlan(
            listOf(card("uinti", "10:00", "Uinti", week = 0), card("retki", "10:00", "Retki", week = 1)),
            alternating = true,
            anchorMonday = monday.toEpochDay()
        ))
        val day = ClientDay(WeekPlan(), listOf(ab), "anna")
        assertEquals(listOf("Uinti"), day.eventsFor(monday).map { it.cardName })
        assertEquals(listOf("Retki"), day.eventsFor(monday.plusWeeks(1)).map { it.cardName })
    }

    @Test
    fun `asiakkaan muutos ryhman kohtaan toimii kuten omaan`() {
        val day = ClientDay(WeekPlan(), listOf(group), "mikko")
        val cancel = EventChange("c1", monday, "grp:tammi:lounas", ChangeType.CANCEL)
        val entries = DayChanges.entries(day.eventsFor(monday), listOf(cancel), monday, null)
        assertEquals(cancel, entries.first { it.original.id == "grp:tammi:lounas" }.change)
        assertTrue(entries.filter { it.original.id != "grp:tammi:lounas" }.all { it.change == null })
    }

    @Test
    fun `muistutus tulee ryhman kohdasta jokaiselle paikalla olevalle jasenelle`() {
        val notifying = group.copy(plan = WeekPlan(listOf(card("lounas", "11:00", "Lounas", notify = true))))
        val plans = mapOf(
            "mikko" to ClientDay(WeekPlan(), listOf(notifying), "mikko"),
            "anna" to ClientDay(WeekPlan(), listOf(notifying), "anna") { _, d -> d == monday }
        )
        val due = WeekSchedule.dueReminders(plans, LocalDateTime.of(monday, java.time.LocalTime.of(11, 0)))
        assertEquals(listOf("mikko"), due.map { it.profileId })
        assertEquals("grp:tammi:lounas", due.single().event.id)
    }
}
