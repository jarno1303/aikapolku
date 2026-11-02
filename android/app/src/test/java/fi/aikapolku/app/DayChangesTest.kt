package fi.aikapolku.app

import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Muutoskortti: päivän muutokset, milloin ne näkyvät ja muistutukset. Puhdas JVM-testi. */
class DayChangesTest {
    // 2.11.2026 on maanantai.
    private val monday = LocalDate.of(2026, 11, 2)

    private fun card(id: String, time: String, day: Int = 1, notify: Boolean = false, before: Int = 0) =
        WeekEvent(id, day, 0, time, EventKind.CARD, cardName = id, notify = notify, notifyBefore = before)

    private val swim = card("Uinti", "10:00", notify = true, before = 15)
    private val lunch = card("Lounas", "11:30")
    private val plan = WeekPlan(listOf(swim, lunch, card("Uinti", "10:00", day = 2).copy(id = "Uinti-ti")))

    private fun change(
        type: ChangeType,
        reveal: ChangeReveal = ChangeReveal.NOW,
        date: LocalDate = monday,
        eventId: String = swim.id
    ) = EventChange(
        id = "c-$type-$date",
        date = date,
        eventId = eventId,
        type = type,
        cardName = if (type == ChangeType.REPLACE) "Ulkoilu" else "",
        cardPhoto = if (type == ChangeType.REPLACE) "builtin:outdoors" else "",
        time = if (type == ChangeType.MOVE) "13:00" else "",
        reveal = reveal
    )

    @Test
    fun `korvaus vaihtaa sisallon mutta pitaa ajan ja tunnuksen`() {
        val e = DayChanges.effective(swim, change(ChangeType.REPLACE))
        assertEquals("Ulkoilu", e.cardName)
        assertEquals("builtin:outdoors", e.cardPhoto)
        assertEquals("10:00", e.time)
        assertEquals(swim.id, e.id)
    }

    @Test
    fun `siirretty jarjestetaan uuteen aikaan`() {
        val entries = DayChanges.entries(listOf(swim, lunch), listOf(change(ChangeType.MOVE)), monday, null)
        assertEquals(listOf("Lounas", "Uinti"), entries.map { it.event.cardName })
        assertEquals("13:00", entries[1].event.time)
        assertEquals("10:00", entries[1].original.time)
    }

    @Test
    fun `peruttu on mukana merkittyna`() {
        val entries = DayChanges.entries(listOf(swim, lunch), listOf(change(ChangeType.CANCEL)), monday, null)
        assertTrue(entries.first { it.original.id == swim.id }.cancelled)
        assertFalse(entries.first { it.original.id == lunch.id }.cancelled)
    }

    @Test
    fun `muutos koskee vain omaa paivaansa`() {
        val entries = DayChanges.entries(listOf(swim), listOf(change(ChangeType.CANCEL, date = monday.plusDays(7))), monday, null)
        assertNull(entries.single().change)
    }

    @Test
    fun `heti nakyy aina`() {
        val c = change(ChangeType.CANCEL, ChangeReveal.NOW, date = monday.plusDays(1), eventId = "Uinti-ti")
        assertTrue(DayChanges.isRevealed(c, plan.events[2], monday.atTime(7, 0)))
    }

    @Test
    fun `aamulla nakyva ei nay edellisena paivana`() {
        val c = change(ChangeType.CANCEL, ChangeReveal.MORNING)
        assertFalse(DayChanges.isRevealed(c, swim, monday.minusDays(1).atTime(20, 0)))
        assertTrue(DayChanges.isRevealed(c, swim, monday.atTime(0, 0)))
    }

    @Test
    fun `tapahtuman aikaan nakyva tulee esiin ennakkomuistutuksen aikaan`() {
        // Uinti klo 10.00, muistutus 15 min ennen -> muutos näkyy klo 9.45.
        val c = change(ChangeType.REPLACE, ChangeReveal.AT_EVENT)
        assertEquals(monday.atTime(9, 45), DayChanges.revealAt(c, swim))
        assertFalse(DayChanges.isRevealed(c, swim, monday.atTime(9, 44)))
        assertTrue(DayChanges.isRevealed(c, swim, monday.atTime(9, 45)))
    }

    @Test
    fun `aiemmaksi siirretty nakyy uuden ajan mukaan`() {
        val c = change(ChangeType.MOVE, ChangeReveal.AT_EVENT).copy(time = "08:30")
        assertEquals(monday.atTime(8, 15), DayChanges.revealAt(c, swim))
    }

    @Test
    fun `kayttaja nakee vain jo naytettavat muutokset ohjaaja kaikki`() {
        val c = change(ChangeType.REPLACE, ChangeReveal.AT_EVENT)
        val user = DayChanges.entries(listOf(swim), listOf(c), monday, monday.atTime(8, 0))
        assertEquals("Uinti", user.single().event.cardName)
        assertNull(user.single().change)
        val caregiver = DayChanges.entries(listOf(swim), listOf(c), monday, null)
        assertEquals("Ulkoilu", caregiver.single().event.cardName)
    }

    @Test
    fun `aloitusnakyma kertoo naytettavat kuittaamattomat muutokset tanaan ja huomenna`() {
        val today = change(ChangeType.CANCEL)
        val tomorrow = change(ChangeType.REPLACE, ChangeReveal.NOW, date = monday.plusDays(1), eventId = "Uinti-ti")
        val hiddenTomorrow = change(ChangeType.CANCEL, ChangeReveal.MORNING, date = monday.plusDays(1), eventId = "Uinti-ti").copy(id = "x")
        val now = monday.atTime(7, 0)
        assertEquals(
            listOf(today.id, tomorrow.id),
            DayChanges.announced(plan, listOf(today, tomorrow), now) { false }.map { it.first.id }
        )
        assertTrue(DayChanges.announced(plan, listOf(hiddenTomorrow), now) { false }.isEmpty())
        // "Selvä" kuitattu -> ei enää aloitusnäkymässä.
        assertTrue(DayChanges.announced(plan, listOf(today), now) { it.id == today.id }.isEmpty())
    }

    @Test
    fun `samalle tapahtumalle ja paivalle on yksi muutos`() {
        val first = change(ChangeType.CANCEL)
        val second = change(ChangeType.MOVE).copy(id = "toinen")
        val all = DayChanges.upsert(DayChanges.upsert(emptyList(), first), second)
        assertEquals(listOf("toinen"), all.map { it.id })
    }

    @Test
    fun `vanhat ja poistettuun tapahtumaan liittyvat siivotaan`() {
        val old = change(ChangeType.CANCEL, date = monday.minusDays(8))
        val orphan = change(ChangeType.CANCEL, eventId = "poistettu")
        val kept = change(ChangeType.MOVE)
        assertEquals(listOf(kept), DayChanges.cleaned(listOf(old, orphan, kept), monday, setOf(swim.id)))
    }

    @Test
    fun `muistutus seuraa muutosta`() {
        val plans = mapOf("p" to WeekPlan(listOf(swim)))
        // Siirretty klo 13.00 -> muistutus 12.45.
        val moved = mapOf("p" to listOf(change(ChangeType.MOVE)))
        assertEquals(monday.atTime(12, 45), WeekSchedule.nextReminderAt(plans, monday.atTime(7, 0), moved))
        // Korvattu -> sama aika, uusi sisältö ja muutos mukana.
        val replaced = WeekSchedule.dueReminders(plans, monday.atTime(9, 45), mapOf("p" to listOf(change(ChangeType.REPLACE))))
        assertEquals("Ulkoilu", replaced.single().event.cardName)
        assertEquals(ChangeType.REPLACE, replaced.single().change?.type)
        // Peruttu -> muutosilmoitus alkuperäiseen aikaan.
        val cancelled = WeekSchedule.dueReminders(plans, monday.atTime(9, 45), mapOf("p" to listOf(change(ChangeType.CANCEL))))
        assertEquals(ChangeType.CANCEL, cancelled.single().change?.type)
        // Ilman muutoksia ennallaan.
        assertNull(WeekSchedule.dueReminders(plans, monday.atTime(9, 45)).single().change)
    }

    // --- Päiväohjelman vaiheen muutos (myös etukäteen) ---

    private val school = WeekEvent("koulu", 1, 0, "08:00", EventKind.PROGRAM, programId = "p1")
    private val schoolProgram = fi.aikapolku.app.data.DayProgram(
        "p1", "Koulupäivä",
        listOf(
            fi.aikapolku.app.data.ProgramStep("s1", "Aamupiiri", "", ""),
            fi.aikapolku.app.data.ProgramStep("s2", "Uinti", "/uinti.jpg", "Halli", voicePath = "/v.m4a", subSteps = listOf("Uimapuku")),
            fi.aikapolku.app.data.ProgramStep("s3", "Lounas", "", "")
        )
    )

    private fun stepChange(type: ChangeType, stepId: String = "s2", showOriginal: Boolean = true) = EventChange(
        id = "sc-$stepId-$type", date = monday, eventId = school.id, type = type,
        cardName = if (type == ChangeType.REPLACE) "Ulkoilu" else "", cardPhoto = if (type == ChangeType.REPLACE) "builtin:outdoors" else "",
        stepId = stepId, stepName = "Uinti", showOriginal = showOriginal
    )

    @Test
    fun `vaihemuutos ei muuta tapahtumaa mutta nakyy rivilla`() {
        val e = DayChanges.entries(listOf(school), listOf(stepChange(ChangeType.REPLACE)), monday, null).single()
        assertEquals(school, e.event)
        assertNull(e.change)
        assertEquals(1, e.stepChanges.size)
        assertEquals(school, DayChanges.effective(school, stepChange(ChangeType.REPLACE)))
    }

    @Test
    fun `koko ohjelman muutos ohittaa vaihemuutokset`() {
        val whole = EventChange("w", monday, school.id, ChangeType.CANCEL)
        val e = DayChanges.entries(listOf(school), listOf(whole, stepChange(ChangeType.REPLACE)), monday, null).single()
        assertTrue(e.cancelled)
        assertTrue(e.stepChanges.isEmpty())
        assertTrue(DayChanges.stepChangesFor(WeekPlan(listOf(school)), listOf(whole, stepChange(ChangeType.REPLACE)), monday, "p1").isEmpty())
    }

    @Test
    fun `saman tapahtuman eri vaiheilla on omat muutoksensa`() {
        val all = DayChanges.upsert(DayChanges.upsert(emptyList(), stepChange(ChangeType.CANCEL, "s1")), stepChange(ChangeType.REPLACE, "s2"))
        assertEquals(2, all.size)
        val whole = EventChange("w", monday, school.id, ChangeType.MOVE, time = "09:00")
        assertEquals(3, DayChanges.upsert(all, whole).size)
        assertEquals("w", DayChanges.wholeChange(DayChanges.upsert(all, whole), monday, school.id)?.id)
    }

    @Test
    fun `ohjelmaan sovelletaan vaihemuutokset`() {
        val applied = DayChanges.applyToProgram(
            schoolProgram,
            listOf(stepChange(ChangeType.CANCEL, "s1"), stepChange(ChangeType.REPLACE, "s2"))
        ) { name, _ -> listOf("Kengät", name) }
        assertTrue(applied.steps[0].cancelledToday)
        val replaced = applied.steps[1]
        assertEquals("Ulkoilu", replaced.name)
        assertEquals("builtin:outdoors", replaced.photo)
        assertEquals("", replaced.voicePath)
        assertEquals(listOf("Kengät", "Ulkoilu"), replaced.subSteps)
        assertEquals("Uinti", replaced.changedFrom)
        assertEquals("s2", replaced.id)
        assertEquals(schoolProgram.steps[2], applied.steps[2])
    }

    @Test
    fun `ilman alkuperaisen nayttoa korvattu vaihe ei kerro vanhaa nimea`() {
        val applied = DayChanges.applyToProgram(schoolProgram, listOf(stepChange(ChangeType.REPLACE, showOriginal = false))) { _, _ -> emptyList() }
        assertTrue(applied.steps[1].changedToday)
        assertEquals("", applied.steps[1].changedFrom)
    }

    @Test
    fun `etukateen tehty vaihemuutos koskee vain sen paivan ohjelmaa`() {
        val plan = WeekPlan(listOf(school))
        val c = stepChange(ChangeType.CANCEL)
        assertEquals(listOf(c), DayChanges.stepChangesFor(plan, listOf(c), monday, "p1"))
        assertTrue(DayChanges.stepChangesFor(plan, listOf(c), monday.plusDays(7), "p1").isEmpty())
        assertTrue(DayChanges.stepChangesFor(plan, listOf(c), monday, "toinen").isEmpty())
    }

    @Test
    fun `aloitusnakyma kertoo myos vaihemuutoksen ja muistutus pysyy ennallaan`() {
        val plan = WeekPlan(listOf(school.copy(notify = true)))
        val c = stepChange(ChangeType.REPLACE)
        assertEquals(listOf(c.id), DayChanges.announced(plan, listOf(c), monday.atTime(7, 0)) { false }.map { it.first.id })
        val due = WeekSchedule.dueReminders(mapOf("p" to plan), monday.atTime(8, 0), mapOf("p" to listOf(c)))
        assertNull(due.single().change)
        assertEquals(EventKind.PROGRAM, due.single().event.kind)
    }
}
