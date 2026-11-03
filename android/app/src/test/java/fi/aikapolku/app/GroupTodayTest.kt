package fi.aikapolku.app

import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupSchedule
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.week.GroupToday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ryhmä tänään: ryhmän päivämuutokset jäsenille, asiakkaan oma muutos edellä, poissaolo. */
class GroupTodayTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private var n = 0
    private val newId = { "c${n++}" }

    private fun card(id: String, time: String, name: String) = WeekEvent(id, 1, 0, time, EventKind.CARD, cardName = name)

    private val group = Group("tammi", "Tammi", listOf("mikko", "anna"), WeekPlan(listOf(card("uinti", "10:00", "Uinti"), card("lounas", "11:00", "Lounas"))))
    private val replaced = GroupToday.replace("uinti", monday, "Elokuva", "builtin:x", newId)

    private fun day(member: String, absent: Boolean = false) = ClientDay(
        WeekPlan(), listOf(group), member,
        absent = { _, _ -> absent },
        groupChanges = listOf(GroupSchedule.toMember("tammi", replaced))
    )

    private fun entries(d: ClientDay, own: List<EventChange> = emptyList()) =
        DayChanges.entries(d.eventsFor(monday), d.withGroupChanges(own), monday, null)

    @Test
    fun `ryhman muutos nakyy jokaiselle paikalla olevalle`() {
        listOf("mikko", "anna").forEach { m ->
            val uinti = entries(day(m)).first { it.original.id == "grp:tammi:uinti" }
            assertEquals("Elokuva", uinti.event.cardName)
            assertEquals(ChangeType.REPLACE, uinti.change?.type)
        }
    }

    @Test
    fun `asiakkaan oma muutos menee ryhman muutoksen edelle`() {
        val own = EventChange("oma", monday, "grp:tammi:uinti", ChangeType.REPLACE, cardName = "Fysioterapia")
        val uinti = entries(day("mikko"), listOf(own)).first { it.original.id == "grp:tammi:uinti" }
        assertEquals("Fysioterapia", uinti.event.cardName)
        // Annalla ryhmän muutos.
        assertEquals("Elokuva", entries(day("anna")).first { it.original.id == "grp:tammi:uinti" }.event.cardName)
    }

    @Test
    fun `poissa olevalle ei tule ryhman ohjelmaa eika muutoksia`() {
        assertTrue(entries(day("mikko", absent = true)).isEmpty())
    }

    @Test
    fun `peru, siirto koko ryhmalle ja runko joka viikko`() {
        val cancel = GroupToday.cancel("lounas", monday, newId)
        val list = GroupToday.entries(group, listOf(cancel), monday)
        assertTrue(list.first { it.original.id == "lounas" }.cancelled)
        assertNull(list.first { it.original.id == "uinti" }.change)
        val moved = GroupToday.moveWeekly(group, "lounas", "11:30")
        assertEquals("11:30", moved.plan.events.first { it.id == "lounas" }.time)
        assertEquals("10:00", moved.plan.events.first { it.id == "uinti" }.time)
    }
}
