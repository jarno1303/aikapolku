package fi.aikapolku.app

import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.Moves
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Raahaus uuteen aikaan: vain tänään (päivän muutos) tai joka viikko. Puhdas JVM-testi. */
class MovesTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private var n = 0
    private val newId = { "uusi${n++}" }

    private fun card(id: String, time: String, name: String, notify: Boolean = false) =
        WeekEvent(id, 1, 0, time, EventKind.CARD, cardName = name, cardPhoto = "builtin:x", notify = notify, notifyBefore = if (notify) 5 else 0)

    private val group = Group("tammi", "Tammi", listOf("mikko", "anna"), WeekPlan(listOf(card("lahto", "15:00", "Lähtö", notify = true))))

    @Test
    fun `vain tanaan tekee siirron ja nayttaa uuden ajan`() {
        val c = Moves.dayChange(emptyList(), "grp:tammi:lahto", monday, "13:00", ChangeReveal.NOW, true, newId)!!
        assertEquals(ChangeType.MOVE, c.type)
        val day = ClientDay(WeekPlan(), listOf(group), "mikko")
        val entry = DayChanges.entries(day.eventsFor(monday), listOf(c), monday, null).single()
        assertEquals("13:00", entry.event.time)
        assertEquals("15:00", entry.original.time)
        // Muut ryhmän asiakkaat eivät muutu (muutos on Mikon).
        assertEquals("15:00", ClientDay(WeekPlan(), listOf(group), "anna").eventsFor(monday).single().time)
    }

    @Test
    fun `korvattu kohta sailyy korvattuna ja siirtyy, peruttua ei siirreta`() {
        val replaced = EventChange("r", monday, "x", ChangeType.REPLACE, cardName = "Uinti")
        val moved = Moves.dayChange(listOf(replaced), "x", monday, "10:30", ChangeReveal.NOW, true, newId)!!
        assertEquals(ChangeType.REPLACE, moved.type)
        assertEquals("r", moved.id)
        assertEquals("10:30", DayChanges.effective(card("x", "09:00", "Ulkoilu"), moved).time)
        assertEquals("Uinti", DayChanges.effective(card("x", "09:00", "Ulkoilu"), moved).cardName)
        val cancelled = EventChange("c", monday, "x", ChangeType.CANCEL)
        assertNull(Moves.dayChange(listOf(cancelled), "x", monday, "10:30", ChangeReveal.NOW, true, newId))
        // Aiempi siirto korvautuu samalla tunnuksella.
        val first = EventChange("m", monday, "x", ChangeType.MOVE, time = "10:00")
        assertEquals("m", Moves.dayChange(listOf(first), "x", monday, "11:00", ChangeReveal.NOW, true, newId)!!.id)
    }

    @Test
    fun `joka viikko oma kohta saa uuden ajan`() {
        val own = WeekPlan(listOf(card("laake", "12:00", "Lääkkeet")))
        val result = Moves.weekly(own, emptyList(), "mikko", "laake", monday, "12:30", newId)!!
        assertEquals("12:30", result.own.events.single().time)
        assertNull(result.group)
    }

    @Test
    fun `joka viikko ryhman kohdasta tulee poikkeus ja oma kohta vain talle asiakkaalle`() {
        val result = Moves.weekly(WeekPlan(), listOf(group), "mikko", "grp:tammi:lahto", monday, "13:00", newId)!!
        val g = result.group!!
        assertEquals(listOf(ExceptionKind.SKIP), g.exceptions.filter { it.memberId == "mikko" }.map { it.kind })
        val mine = result.own.events.single()
        assertEquals("13:00", mine.time)
        assertEquals("Lähtö", mine.cardName)
        assertTrue(mine.notify)
        assertEquals(5, mine.notifyBefore)
        // Mikon päivässä lähtö klo 13, Annalla edelleen klo 15.
        assertEquals(listOf("13:00"), ClientDay(result.own, listOf(g), "mikko").eventsFor(monday).map { it.time })
        assertEquals(listOf("15:00"), ClientDay(WeekPlan(), listOf(g), "anna").eventsFor(monday).map { it.time })
        // Seuraavallakin viikolla.
        assertEquals(listOf("13:00"), ClientDay(result.own, listOf(g), "mikko").eventsFor(monday.plusWeeks(1)).map { it.time })
    }

    @Test
    fun `vuoroviikoilla oma kohta tulee molemmille viikoille`() {
        val own = WeekPlan(alternating = true, anchorMonday = monday.toEpochDay())
        val result = Moves.weekly(own, listOf(group), "mikko", "grp:tammi:lahto", monday, "13:00", newId)!!
        assertEquals(setOf(0, 1), result.own.events.map { it.week }.toSet())
    }
}
