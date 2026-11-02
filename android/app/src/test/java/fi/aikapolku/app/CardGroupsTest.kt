package fi.aikapolku.app

import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.cardsByDayTime
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.DayTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Korttien ryhmittely vuorokauden mukaan (valinta ja päiväohjelman muokkain). */
class CardGroupsTest {

    private val texts: (Int) -> String = { res -> CardArt.templates.first { it.nameRes == res }.legacyName }

    private fun own(name: String, time: DayTime) = CardItem("id_$name", name, "", "/kuva", dayTime = time)

    @Test
    fun `ryhmat ovat aamu paiva ilta milloin tahansa`() {
        val groups = cardsByDayTime(emptyList(), texts)
        assertEquals(listOf(DayTime.MORNING, DayTime.DAY, DayTime.EVENING, DayTime.ANYTIME), groups.map { it.first })
        assertEquals("Aamupala", groups[0].second.first().name)
        assertEquals("Nukkumaan", groups[2].second.last().name)
        assertTrue(groups[3].second.any { it.name == "Ruokailu" })
        assertEquals(CardArt.templates.size, groups.sumOf { it.second.size })
    }

    @Test
    fun `oma kortti on ryhmansa alussa`() {
        val groups = cardsByDayTime(listOf(own("Uinti", DayTime.DAY), own("Iltapala", DayTime.EVENING)), texts).toMap()
        assertEquals("Uinti", groups.getValue(DayTime.DAY).first().name)
        assertEquals("Iltapala", groups.getValue(DayTime.EVENING).first().name)
    }

    @Test
    fun `oma kortti korvaa samannimisen valmiin`() {
        val groups = cardsByDayTime(listOf(own("ruokailu", DayTime.DAY)), texts)
        val all = groups.flatMap { it.second }
        assertEquals(1, all.count { it.name.equals("Ruokailu", ignoreCase = true) })
        assertFalse(groups.toMap().getValue(DayTime.ANYTIME).any { it.name == "Ruokailu" })
    }

    @Test
    fun `valmiin kortin kuva on builtin-tunnus`() {
        val first = cardsByDayTime(emptyList(), texts).first().second.first()
        assertEquals("builtin:breakfast", first.photoPath)
        assertEquals(DayTime.MORNING, first.dayTime)
    }

    @Test
    fun `tuntematon ajankohta tulkitaan milloin tahansa`() {
        assertEquals(DayTime.ANYTIME, DayTime.from(null))
        assertEquals(DayTime.ANYTIME, DayTime.from("aamuyo"))
        assertEquals(DayTime.EVENING, DayTime.from("evening"))
    }

    @Test
    fun `lounas paivalla ja paivallinen seka iltapala illalla`() {
        val groups = cardsByDayTime(emptyList(), texts).toMap()
        assertTrue(groups.getValue(DayTime.DAY).any { it.photoPath == "builtin:lunch_meal" })
        val evening = groups.getValue(DayTime.EVENING).map { it.photoPath }
        assertEquals("builtin:dinner", evening.first())
        assertTrue(evening.indexOf("builtin:evening_snack") < evening.indexOf("builtin:sleep"))
    }
}
