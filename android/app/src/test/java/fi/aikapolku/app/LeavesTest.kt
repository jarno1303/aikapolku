package fi.aikapolku.app

import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.Leave
import fi.aikapolku.app.data.LeaveReason
import fi.aikapolku.app.data.Leaves
import fi.aikapolku.app.data.Profile
import fi.aikapolku.app.data.UnitScope
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Loma / poissaolo ajanjaksona ja laitteen rajaus alayksikköön. */
@RunWith(RobolectricTestRunner::class)
class LeavesTest {
    // 2026-11-20 perjantai … 2026-11-22 sunnuntai.
    private val friday = LocalDate.of(2026, 11, 20)
    private val saturday = friday.plusDays(1)
    private val sunday = friday.plusDays(2)

    private fun card(id: String, day: Int, time: String, name: String) =
        WeekEvent(id, day, 0, time, EventKind.CARD, cardName = name, cardPhoto = "builtin:x")

    private val villa = Group("villa", "Villa", kind = GroupKind.UNIT, plan = WeekPlan((1..7).flatMap { d ->
        listOf(card("lounas$d", d, "11:00", "Lounas"), card("paivallinen$d", d, "17:00", "Päivällinen"), card("iltapala$d", d, "20:00", "Iltapala"))
    }))
    private val omena = Group("omena", "Omena", listOf("matti", "pekka"), kind = GroupKind.UNIT, parentId = "villa")
    private val paaryna = Group("paaryna", "Päärynä", listOf("anna"), kind = GroupKind.UNIT, parentId = "villa")
    private val paiva = Group("paiva", "Päivätoiminta", listOf("matti"), kind = GroupKind.ACTIVITY,
        sessions = (1..5).map { ActivitySession(it, "09:00", "14:00") })
    private val all = listOf(villa, omena, paaryna, paiva)
    private val own = WeekPlan((1..7).map { card("laake$it", it, "08:00", "Lääke") })

    // Kotiloma pe klo 15 – su klo 18.
    private val leave = Leave("l1", "matti", friday.atTime(15, 0), sunday.atTime(18, 0), LeaveReason.HOME)
    private fun matti() = ClientDay(own, Units.groupsFor(all, "matti"), "matti", leaves = listOf(leave), leaveTitle = { "Kotilomalla" })
    private fun names(d: LocalDate) = matti().eventsFor(d).map { it.cardName }

    @Test
    fun `loman alkupaiva aamupaiva normaalisti ja lomakortti alusta`() {
        val fri = names(friday)
        assertTrue("Päivätoiminta" in fri)
        assertTrue("Lääke" in fri)
        assertFalse("Päivällinen" in fri)
        assertEquals("15:00", matti().eventsFor(friday).first { it.cardName == "Kotilomalla" }.time)
    }

    @Test
    fun `koko lomapaiva vain omat kohdat ja lomakortti`() {
        assertEquals(listOf("Kotilomalla", "Lääke"), names(saturday))
    }

    @Test
    fun `paluupaiva yksikon kohdat paluusta alkaen`() {
        val sun = matti().eventsFor(sunday)
        val card = sun.first { it.cardName == "Kotilomalla" }
        assertEquals("00:00", card.time)
        assertEquals("18:00", card.until)
        val names = sun.map { it.cardName }
        assertFalse("Lounas" in names || "Päivällinen" in names)
        assertTrue("Iltapala" in names)
        // Klo 18.30 (aamun lääke kuitattu) korostus on jo seuraavassa: lomakortti päättyi klo 18.
        val i = WeekSchedule.currentIndex(sun, LocalTime.of(18, 30), setOf("laake7"))
        assertEquals("Iltapala", sun[i].cardName)
    }

    @Test
    fun `loma koskee vain kyseista asukasta`() {
        val pekka = ClientDay(WeekPlan(), Units.groupsFor(all, "pekka"), "pekka", leaves = emptyList())
        assertTrue("Päivällinen" in pekka.eventsFor(saturday).map { it.cardName })
    }

    @Test
    fun `lomakortti loytyy tunnuksella ja tallennus sailyy`() {
        assertEquals("15:00", matti().event(leave.eventId)?.time)
        assertTrue(leave.eventId in matti().eventIds())
        assertEquals(listOf(leave), Leaves.decode(Leaves.encode(listOf(leave))))
    }

    @Test
    fun `laitteen rajaus alayksikkoon`() {
        val profiles = listOf(Profile("matti", "Matti"), Profile("pekka", "Pekka"), Profile("anna", "Anna"))
        assertEquals(listOf("matti", "pekka"), UnitScope.filter(profiles, "omena", all, "matti").map { it.id })
        // Nykyinen asiakas näkyy aina, kaikki ilman rajausta.
        assertEquals(listOf("matti", "pekka", "anna"), UnitScope.filter(profiles, "omena", all, "anna").map { it.id })
        assertEquals(3, UnitScope.filter(profiles, "", all, "matti").size)
        assertEquals(3, UnitScope.filter(profiles, "villa", all, "matti").size)
    }

    @Test
    fun `toiminnan kokoontuminen loytyy tulevalle paivalle (arkipyha)`() {
        val dec24 = LocalDate.of(2026, 12, 24) // torstai
        assertEquals(1, fi.aikapolku.app.week.GroupToday.sessionEvents(paiva, dec24).size)
        assertTrue(fi.aikapolku.app.week.GroupToday.sessionEvents(paiva, dec24.plusDays(2)).isEmpty()) // la
    }
}
