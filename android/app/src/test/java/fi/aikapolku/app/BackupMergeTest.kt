package fi.aikapolku.app

import fi.aikapolku.app.backup.BackupMerge
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tuonti nykyiseen profiiliin: mitään ei korvata, samanlaiset ohitetaan. */
class BackupMergeTest {
    private var counter = 0
    private val newId = { "uusi${++counter}" }

    private fun step(name: String, photo: String = "") = ProgramStep("s-$name", name, photo, "")
    private fun program(id: String, name: String, vararg steps: ProgramStep) = DayProgram(id, name, steps.toList())

    @Test
    fun `samanlainen ohjelma ohitetaan vaikka kuvan polku eroaa`() {
        val existing = listOf(program("a", "Aamu", step("Aamupala", "/vanha/kuva.jpg")))
        val incoming = listOf(program("x", "Aamu", step("Aamupala", "/tuotu/kuva.jpg")))
        val r = BackupMerge.programs(existing, incoming, newId)
        assertTrue(r.added.isEmpty())
        assertEquals(1, r.skipped)
        assertEquals("a", r.idMap["x"])
    }

    @Test
    fun `erilainen samanniminen saa numeron ja uuden tunnuksen`() {
        val existing = listOf(program("a", "Aamu", step("Aamupala")))
        val incoming = listOf(program("a", "Aamu", step("Pukeutuminen")))
        val r = BackupMerge.programs(existing, incoming, newId)
        assertEquals("Aamu (2)", r.added.single().name)
        assertEquals("uusi1", r.added.single().id)
        assertEquals("uusi1", r.idMap["a"])
    }

    @Test
    fun `kortit yhdistetaan samoin`() {
        val existing = listOf(CardItem("c1", "Uinti", "", "/a.jpg"))
        val incoming = listOf(CardItem("t1", "Uinti", "", "/b.jpg"), CardItem("t2", "Uinti", "Halli", "/c.jpg"))
        val r = BackupMerge.cards(existing, incoming, newId)
        assertEquals(1, r.skipped)
        assertEquals("Uinti (2)", r.added.single().name)
    }

    @Test
    fun `viikko-ohjelma osoittaa tuotuihin ohjelmiin ja puuttuvat jaavat pois`() {
        val plan = WeekPlan(
            listOf(
                WeekEvent("e1", 1, 0, "08:00", EventKind.PROGRAM, programId = "x"),
                WeekEvent("e2", 1, 0, "09:00", EventKind.PROGRAM, programId = "puuttuu"),
                WeekEvent("e3", 1, 0, "10:00", EventKind.CARD, cardName = "Lääkkeet")
            ),
            alternating = true,
            anchorMonday = 5L
        )
        val mapped = BackupMerge.week(plan, mapOf("x" to "a"), newId)
        assertEquals(listOf("a", ""), mapped.events.map { it.programId })
        assertTrue(mapped.events.none { it.id in setOf("e1", "e3") })
        assertTrue(mapped.alternating)
        assertEquals(5L, mapped.anchorMonday)
    }

    @Test
    fun `ilman henkilotietoja omat kuvat ja aanet poistuvat`() {
        val p = program(
            "a", "Aamu",
            step("Aamupala", "/files/oma.jpg").copy(voicePath = "/files/aani.m4a", autoPlayVoice = true),
            step("Mummola", "/files/mummo.jpg"),
            step("Pukeutuminen", "builtin:dress")
        )
        val clean = BackupMerge.anonymized(p) { name -> if (name == "Aamupala") "builtin:breakfast" else null }
        assertEquals(listOf("builtin:breakfast", "", "builtin:dress"), clean.steps.map { it.photo })
        assertTrue(clean.steps.all { it.voicePath.isEmpty() && !it.autoPlayVoice })
    }
}
