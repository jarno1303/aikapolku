package fi.aikapolku.app

import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.ActivityView
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupSchedule
import fi.aikapolku.app.data.MemberTime
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yksikkömalli (P10, Y1–Y8): yksiköt ja alayksiköt, päivän toiminnat, asukkaan oma aika,
 * sijaintisääntö ja päällekkäisyydet. Puhdas JVM-testi.
 */
class UnitsTest {
    // 2026-11-16 on maanantai, 2026-11-21 lauantai.
    private val monday = LocalDate.of(2026, 11, 16)
    private val saturday = LocalDate.of(2026, 11, 21)

    private fun card(id: String, day: Int, time: String, name: String) =
        WeekEvent(id, day, 0, time, EventKind.CARD, cardName = name, cardPhoto = "builtin:x")

    private fun meals(days: IntRange) = WeekPlan(days.flatMap { d ->
        listOf(
            card("aamupala$d", d, "08:00", "Aamupala"),
            card("lounas$d", d, "11:00", "Lounas"),
            card("valipala$d", d, "14:30", "Välipala"),
            card("paivallinen$d", d, "17:00", "Päivällinen"),
            card("iltapala$d", d, "20:00", "Iltapala")
        )
    })

    // Villa Metsäketo (yhteiset ruokailut) → Omena (saunavuoro) ja Päärynä.
    private val villa = Group("villa", "Villa Metsäketo", kind = GroupKind.UNIT, plan = meals(1..7))
    private val omena = Group("omena", "Omena", listOf("matti", "liisa", "pekka"), kind = GroupKind.UNIT, parentId = "villa",
        plan = WeekPlan(listOf(card("sauna", 3, "18:30", "Sauna"))))
    private val paaryna = Group("paaryna", "Päärynä", listOf("anna", "ville"), kind = GroupKind.UNIT, parentId = "villa")
    private val paiva = Group("paiva", "Päivätoiminta", listOf("matti", "liisa", "anna"), kind = GroupKind.ACTIVITY,
        sessions = (1..5).map { ActivitySession(it, "09:00", "14:00") },
        memberTimes = listOf(MemberTime("liisa", start = "10:00", end = "13:00")),
        cardPhoto = "builtin:schoolwork",
        plan = WeekPlan((1..5).flatMap { d -> listOf(card("kokous$d", d, "09:00", "Aamukokous"), card("plounas$d", d, "11:30", "Lounas päivätoiminnassa")) }))
    private val paja = Group("paja", "Työpaja", listOf("ville"), kind = GroupKind.ACTIVITY,
        sessions = listOf(1, 3, 5).map { ActivitySession(it, "09:30", "13:30") })
    private val all = listOf(villa, omena, paaryna, paiva, paja)

    private fun day(memberId: String, view: ActivityView = ActivityView.CARD, changes: List<EventChange> = emptyList(),
                    absent: (String, LocalDate) -> Boolean = { _, _ -> false }) =
        ClientDay(WeekPlan(), Units.groupsFor(all, memberId), memberId, changes, absent, view)

    private fun names(events: List<WeekEvent>) = events.map { it.cardName }

    @Test
    fun `alayksikon asukas saa ylayksikon ruokailut ja oman yksikon kohdat`() {
        val pekka = day("pekka") // ei toimintaa
        val wednesday = monday.plusDays(2)
        assertEquals(
            listOf("Aamupala", "Lounas", "Välipala", "Päivällinen", "Sauna", "Iltapala"),
            names(pekka.eventsFor(wednesday))
        )
        // Päärynän asukas ei saa Omenan saunaa.
        assertFalse("Päärynä näki Omenan kohdan", "Sauna" in names(day("ville").eventsFor(wednesday)))
    }

    @Test
    fun `toiminnan aikana yksikon kohdat eivat nay asukkaalle`() {
        val matti = names(day("matti").eventsFor(monday))
        assertEquals(listOf("Aamupala", "Päivätoiminta", "Välipala", "Päivällinen", "Iltapala"), matti)
        // Kotona oleva näkee lounaan samaan aikaan.
        assertTrue("Lounas" in names(day("pekka").eventsFor(monday)))
    }

    @Test
    fun `toimintakortti kestaa toiminnan loppuun`() {
        val card = day("matti").eventsFor(monday).first { it.cardName == "Päivätoiminta" }
        assertTrue(card.ongoing)
        assertEquals("09:00", card.time)
        assertEquals("14:00", card.until)
        assertEquals("builtin:schoolwork", card.cardPhoto)
    }

    @Test
    fun `korostus siirtyy eteenpain, kun toiminta paattyy`() {
        val events = day("matti").eventsFor(monday)
        val during = WeekSchedule.currentIndex(events, LocalTime.of(12, 0))
        assertEquals("Päivätoiminta", events[during].cardName)
        // Klo 14.10: toiminta ohi, seuraava on välipala klo 14.30 (väliaika).
        val after = WeekSchedule.currentIndex(events, LocalTime.of(14, 10))
        assertEquals("Välipala", events[after].cardName)
    }

    @Test
    fun `asukkaan oma aika saman toiminnan sisalla`() {
        val liisa = day("liisa").eventsFor(monday)
        val card = liisa.first { it.cardName == "Päivätoiminta" }
        assertEquals("10:00", card.time)
        assertEquals("13:00", card.until)
        // Liisa on kotona klo 9 ja 13.30, joten näkee lounaan klo 11? Ei: klo 11 hän on toiminnassa.
        assertFalse("Lounas" in names(liisa))
        assertTrue("Aamupala" in names(liisa))
    }

    @Test
    fun `eri asukkailla eri toiminta samaan aikaan`() {
        val anna = names(day("anna").eventsFor(monday))
        val ville = names(day("ville").eventsFor(monday))
        assertTrue("Päivätoiminta" in anna && "Työpaja" !in anna)
        assertTrue("Työpaja" in ville && "Päivätoiminta" !in ville)
        assertFalse("Lounas" in anna || "Lounas" in ville)
        // Tiistaina pajaa ei ole: Ville on kotona ja näkee lounaan.
        assertTrue("Lounas" in names(day("ville").eventsFor(monday.plusDays(1))))
    }

    @Test
    fun `viikonloppuna ei toimintaa, vain yhteiset ruokailut`() {
        assertEquals(
            listOf("Aamupala", "Lounas", "Välipala", "Päivällinen", "Iltapala"),
            names(day("matti").eventsFor(saturday))
        )
    }

    @Test
    fun `peruttu toiminta palauttaa yksikon kohdat ja nakyy muutoksena`() {
        val sessionId = GroupSchedule.eventId("paiva", Units.sessionEventId(1))
        val cancel = EventChange("c1", monday, sessionId, ChangeType.CANCEL, reason = "Ohjaaja sairas")
        val matti = day("matti", changes = listOf(cancel))
        val events = matti.eventsFor(monday)
        assertTrue("Lounas palasi", "Lounas" in names(events))
        val entry = DayChanges.entries(events, listOf(cancel), monday, null).first { it.event.id == sessionId }
        assertTrue("Peruttu toiminta näkyy perutuksi", entry.cancelled)
    }

    @Test
    fun `poissaolo toiminnasta = kotona`() {
        val matti = day("matti", absent = { g, d -> g == "paiva" && d == monday })
        val events = names(matti.eventsFor(monday))
        assertTrue("Lounas" in events)
        assertFalse("Päivätoiminta" in events)
    }

    @Test
    fun `poissaolo yksikosta ei tuo ylayksikon runkoa`() {
        val pekka = ClientDay(WeekPlan(), Units.groupsFor(all, "pekka"), "pekka", absent = { g, _ -> g == "villa" || g == "omena" })
        assertTrue(pekka.eventsFor(monday).isEmpty())
    }

    @Test
    fun `toiminnan runko asukkaalle, joka haluaa nahda pidemmalle`() {
        val matti = names(day("matti", view = ActivityView.PLAN).eventsFor(monday))
        assertTrue("Aamukokous" in matti && "Lounas päivätoiminnassa" in matti)
        assertFalse("Päivätoiminta" in matti)
        assertFalse("Lounas" in matti)
        // Liisalla runko vain hänen omana aikanaan (klo 10–13): ei aamukokousta klo 9.
        val liisa = names(day("liisa", view = ActivityView.PLAN).eventsFor(monday))
        assertFalse("Aamukokous" in liisa)
        assertTrue("Lounas päivätoiminnassa" in liisa)
    }

    @Test
    fun `yksikon muutos ei koske toiminnassa olevaa`() {
        val lunch = GroupSchedule.toMember("villa", EventChange("m1", monday, "lounas1", ChangeType.MOVE, time = "12:00"))
        val matti = day("matti", changes = listOf(lunch))
        val announced = DayChanges.announced(matti, listOf(lunch), LocalDateTime.of(monday, LocalTime.of(8, 0))) { false }
        assertTrue("Toiminnassa oleva sai yksikön muutoksen", announced.isEmpty())
        val pekka = day("pekka", changes = listOf(lunch))
        assertEquals(1, DayChanges.announced(pekka, listOf(lunch), LocalDateTime.of(monday, LocalTime.of(8, 0))) { false }.size)
    }

    @Test
    fun `ohjaaja nakee piilotetut kohdat`() {
        val hidden = day("matti").hiddenFor(monday)
        assertEquals(listOf("Lounas"), hidden.map { it.first.cardName })
        assertEquals("Päivätoiminta", hidden.single().second.name)
        assertTrue(day("pekka").hiddenFor(monday).isEmpty())
    }

    @Test
    fun `siirtymakortit vain kun ohjaaja ottaa ne kayttoon`() {
        assertFalse(names(day("matti").eventsFor(monday)).any { it.startsWith("Lähtö") })
        val withCards = all.map { if (it.id == "paiva") it.copy(leaveCard = "Lähtö päivätoimintaan", returnCard = "Paluu kotiin") else it }
        val matti = ClientDay(WeekPlan(), Units.groupsFor(withCards, "matti"), "matti")
        val events = matti.eventsFor(monday)
        assertEquals("08:50", events.first { it.cardName == "Lähtö päivätoimintaan" }.time)
        assertEquals("14:00", events.first { it.cardName == "Paluu kotiin" }.time)
    }

    @Test
    fun `asukas ei nae toisen asukkaan tietoja`() {
        // Matin poikkeus tai oma aika ei vaikuta Annaan.
        val anna = day("anna").eventsFor(monday).first { it.cardName == "Päivätoiminta" }
        assertEquals("09:00", anna.time)
        assertNull(day("pekka").eventsFor(monday).firstOrNull { it.cardName == "Päivätoiminta" })
    }

    @Test
    fun `paallekkaiset toiminnat havaitaan`() {
        val both = all.map { if (it.id == "paja") it.copy(members = it.members + "matti") else it }
        val conflicts = Units.conflicts(both, "matti")
        assertEquals(listOf(1, 3, 5), conflicts.map { it.day })
        assertTrue(Units.conflicts(all, "matti").isEmpty())
    }

    @Test
    fun `kokoontumiskortti loytyy tunnuksella (muutokset ja ilmoitukset)`() {
        val id = GroupSchedule.eventId("paiva", Units.sessionEventId(2))
        val event = day("liisa").event(id)
        assertEquals("10:00", event?.time)
        assertEquals("13:00", event?.until)
        assertTrue(id in day("liisa").eventIds())
    }

    @Test
    fun `silmukka yksikkoketjussa ei jumita`() {
        val a = Group("a", "A", listOf("x"), kind = GroupKind.UNIT, parentId = "b")
        val b = Group("b", "B", kind = GroupKind.UNIT, parentId = "a")
        assertEquals(listOf("b"), Units.ancestors(a, listOf(a, b)).map { it.id })
    }

    @Test
    fun `vanha ryhma toimii ennallaan`() {
        val old = Group("tammi", "Tammi", listOf("mikko"), WeekPlan(listOf(card("lounas", 1, "11:00", "Lounas"))))
        val mikko = ClientDay(WeekPlan(), listOf(old), "mikko")
        assertEquals(listOf("Lounas"), names(mikko.eventsFor(monday)))
    }

    @Test
    fun `oma aika valituille paiville, muina paivina ei toiminnassa`() {
        val g = paiva.copy(memberTimes = listOf(MemberTime("liisa", setOf(1, 3), "10:00", "13:00")))
        val groups = all.map { if (it.id == "paiva") g else it }
        fun liisa(d: LocalDate) = ClientDay(WeekPlan(), Units.groupsFor(groups, "liisa"), "liisa").eventsFor(d).map { it.cardName }
        assertTrue("Päivätoiminta" in liisa(monday))
        val tuesday = liisa(monday.plusDays(1))
        assertFalse("Päivätoiminta" in tuesday)
        assertTrue("Lounas" in tuesday)
    }

    @Test
    fun `siirretty lounas ratkaistaan uudella ajallaan`() {
        // Lounas siirretään klo 14.15: toiminnasta palannut Matti näkee sen.
        val moved = GroupSchedule.toMember("villa", EventChange("m2", monday, "lounas1", ChangeType.MOVE, time = "14:15"))
        assertTrue("Lounas" in names(day("matti", changes = listOf(moved)).eventsFor(monday)))
        assertTrue(day("matti", changes = listOf(moved)).hiddenFor(monday).isEmpty())
    }

    @Test
    fun `siirtymakortti loytyy tunnuksella`() {
        val withCards = all.map { if (it.id == "paiva") it.copy(leaveCard = "Lähtö", returnCard = "Paluu kotiin") else it }
        val matti = ClientDay(WeekPlan(), Units.groupsFor(withCards, "matti"), "matti")
        val id = GroupSchedule.eventId("paiva", "return-1")
        assertEquals("14:00", matti.event(id)?.time)
        assertTrue(id in matti.eventIds())
    }

    @Test
    fun `ohjaajan etusivu nayttaa yksikon, alayksikon rungolla ja toiminnat`() {
        val shown = fi.aikapolku.app.week.GroupToday.shown(all).map { it.id }
        assertTrue("villa" in shown && "omena" in shown && "paiva" in shown && "paja" in shown)
        assertFalse("Päärynä ilman runkoa ei näy erikseen", "paaryna" in shown)
        assertEquals(listOf("matti", "liisa", "pekka", "anna", "ville"), fi.aikapolku.app.week.GroupToday.membersOf(villa, all))
        // Poissaolo yksiköstä merkitään asukkaan alayksikköön.
        assertEquals("omena", fi.aikapolku.app.week.GroupToday.absenceGroup(villa, "matti", all))
        // Toiminnan kokoontuminen on peruttavissa.
        val entries = fi.aikapolku.app.week.GroupToday.dayEntries(paiva, emptyList(), monday)
        assertEquals(Units.sessionEventId(1), entries.first().original.id)
    }
}
