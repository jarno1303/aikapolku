package fi.aikapolku.app

import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.MemberException
import fi.aikapolku.app.data.Runko
import fi.aikapolku.app.data.RunkoTemplate
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rungon muokkaus aikajanalla ja valmiit pohjat. Puhdas JVM-testi. */
class RunkoTest {
    private var counter = 0
    private val newId = { "id${counter++}" }

    private fun card(id: String, day: Int, time: String, week: Int = 0) =
        WeekEvent(id, day, week, time, EventKind.CARD, cardName = id)

    @Test
    fun `ehdotettu aika on valien puolivalissa viiden minuutin tarkkuudella`() {
        assertEquals("10:00", Runko.suggestTime("09:00", "11:00"))
        assertEquals("11:15", Runko.suggestTime("11:00", "11:30"))
        assertEquals("12:00", Runko.suggestTime("11:00", null))
        assertEquals("08:00", Runko.suggestTime(null, "09:00"))
        assertEquals("08:00", Runko.suggestTime(null, null))
        assertEquals("23:55", Runko.suggestTime("23:30", null))
        assertEquals("00:00", Runko.suggestTime(null, "00:30"))
        // Kapea väli: ei ennen edellistä eikä seuraavan jälkeen.
        val narrow = WeekSchedule.minutesOf(Runko.suggestTime("10:00", "10:05"))
        assertTrue(narrow in 600..605)
    }

    @Test
    fun `kortin lisays tulee valitulle paivalle ja viikolle ilman muistutusta`() {
        val plan = Runko.addCard(WeekPlan(), 2, 1, "10:30", "Lounas", "builtin:lunch_meal", newId)
        val e = plan.events.single()
        assertEquals(2, e.day)
        // Ilman vuoroviikkoja kaikki menee viikolle A.
        assertEquals(WeekSchedule.WEEK_A, e.week)
        assertEquals(EventKind.CARD, e.kind)
        assertEquals(false, e.notify)
        val alternating = Runko.addCard(WeekPlan(alternating = true), 2, 1, "10:30", "Lounas", "", newId)
        assertEquals(1, alternating.events.single().week)
    }

    @Test
    fun `paivan kopiointi korvaa kohdepaivat ja saa uudet tunnukset`() {
        val plan = WeekPlan(listOf(card("a", 1, "09:00"), card("b", 1, "11:00"), card("vanha", 3, "08:00"), card("su", 7, "10:00")))
        val copied = Runko.copyDay(plan, 1, 0, setOf(1, 2, 3), newId)
        assertEquals(listOf("09:00", "11:00"), WeekSchedule.eventsOn(copied, 2, 0).map { it.time })
        assertEquals(listOf("09:00", "11:00"), WeekSchedule.eventsOn(copied, 3, 0).map { it.time })
        assertTrue(copied.events.none { it.id == "vanha" })
        assertEquals(listOf("a", "b"), WeekSchedule.eventsOn(copied, 1, 0).map { it.id })
        assertEquals(1, WeekSchedule.eventsOn(copied, 7, 0).size)
        assertEquals(copied.events.size, copied.events.map { it.id }.toSet().size)
    }

    @Test
    fun `paivan tyhjennys koskee vain sita paivaa`() {
        val plan = WeekPlan(listOf(card("a", 1, "09:00"), card("b", 2, "09:00")))
        assertEquals(listOf("b"), Runko.clearDay(plan, 1, 0).events.map { it.id })
    }

    @Test
    fun `kopio toisesta ottaa vain kortit uusin tunnuksin`() {
        val source = WeekPlan(
            listOf(card("a", 1, "09:00"), WeekEvent("p", 1, 0, "10:00", EventKind.PROGRAM, programId = "x")),
            alternating = true, anchorMonday = 123
        )
        val copy = Runko.copyFrom(source, newId)
        assertEquals(1, copy.events.size)
        assertTrue(copy.events.single().id != "a")
        assertEquals(true, copy.alternating)
        assertEquals(123L, copy.anchorMonday)
    }

    @Test
    fun `pohjat kayttavat olemassa olevia kortteja ja oikeita paivia`() {
        val ids = CardArt.templates.map { it.id }.toSet()
        RunkoTemplate.all.forEach { t ->
            assertTrue(t.id, t.items.all { it.second in ids })
            val plan = t.build({ "nimi:$it" }, newId)
            assertEquals(t.days.size * t.items.size, plan.events.size)
            assertTrue(plan.events.all { it.kind == EventKind.CARD && it.cardPhoto.startsWith("builtin:") && it.cardName.startsWith("nimi:") })
            // Kellonajat nousevassa järjestyksessä.
            val minutes = t.items.map { WeekSchedule.minutesOf(it.first) }
            assertEquals(minutes.sorted(), minutes)
        }
        val day = RunkoTemplate.all.first { it.id == "day_activity" }
        assertEquals((1..5).toSet(), day.days)
    }

    @Test
    fun `poikkeus asetetaan, vaihdetaan ja poistetaan`() {
        var g = Group("g", "Tammi", listOf("m"), WeekPlan(listOf(card("lounas", 1, "11:00"))))
        g = Runko.withException(g, "lounas", "m", MemberException("lounas", "m", ExceptionKind.SKIP))
        g = Runko.withException(g, "lounas", "m", MemberException("lounas", "m", ExceptionKind.REPLACE, "Eväät", ""))
        assertEquals(listOf(ExceptionKind.REPLACE), g.exceptions.map { it.kind })
        assertTrue(Runko.withException(g, "lounas", "m", null).exceptions.isEmpty())
        val removed = Runko.withoutEvent(g, "lounas")
        assertTrue(removed.plan.events.isEmpty() && removed.exceptions.isEmpty())
    }

    private fun day(vararg items: Pair<String, String>) =
        WeekPlan(items.map { (id, time) -> card(id, 1, time) } + card("tiistai", 2, "09:00"))

    private fun order(plan: WeekPlan) = WeekSchedule.eventsOn(plan, 1, 0).map { it.time to it.id }

    @Test
    fun `siirto alas - ajat pysyvat ja sisallot liukuvat`() {
        val plan = day("kokous" to "09:00", "ulkoilu" to "10:00", "lounas" to "11:00", "askartelu" to "12:30")
        val moved = Runko.moveContent(plan, 1, 0, 1, 3)
        assertEquals(
            listOf("09:00" to "kokous", "10:00" to "lounas", "11:00" to "askartelu", "12:30" to "ulkoilu"),
            order(moved)
        )
        // Toinen päivä ennallaan.
        assertEquals(listOf("09:00"), WeekSchedule.eventsOn(moved, 2, 0).map { it.time })
    }

    @Test
    fun `siirto ylos alkuun ja sisallon asetukset kulkevat mukana`() {
        val plan = WeekPlan(listOf(
            card("kokous", 1, "09:00"),
            card("lounas", 1, "11:00"),
            card("laake", 1, "12:00").copy(notify = true, notifyBefore = 5, ongoing = false)
        ))
        val moved = Runko.moveContent(plan, 1, 0, 2, 0)
        assertEquals(listOf("09:00" to "laake", "11:00" to "kokous", "12:00" to "lounas"), order(moved))
        val laake = moved.events.first { it.id == "laake" }
        assertTrue(laake.notify)
        assertEquals(5, laake.notifyBefore)
        // Sama paikka tai virheellinen kohta: ei muutosta.
        assertEquals(plan, Runko.moveContent(plan, 1, 0, 1, 1))
        assertEquals(plan, Runko.moveContent(plan, 1, 0, 0, 9))
    }

    @Test
    fun `siirto toiseen paivaan sailyttaa ajan`() {
        val plan = day("kokous" to "09:00")
        val moved = Runko.moveToDay(plan, "kokous", 4)
        assertEquals(4, moved.events.first { it.id == "kokous" }.day)
        assertEquals("09:00", moved.events.first { it.id == "kokous" }.time)
        assertEquals(plan, Runko.moveToDay(plan, "kokous", 9))
    }

    @Test
    fun `pudotuskohta lasketaan sormen ja muiden keskikohtien mukaan`() {
        val centers = listOf(100f, 200f, 300f)
        assertEquals(0, Runko.dropIndex(centers, 50f))
        assertEquals(1, Runko.dropIndex(centers, 150f))
        assertEquals(3, Runko.dropIndex(centers, 999f))
    }
}
