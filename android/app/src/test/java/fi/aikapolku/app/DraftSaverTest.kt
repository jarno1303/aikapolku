package fi.aikapolku.app

import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.parseDraft
import fi.aikapolku.app.data.serializeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Muokkausluonnoksen tallennus ja palautus (rememberSaveable).
 * Luonnos ei saa kadota esimerkiksi näytön kääntyessä.
 */
@RunWith(RobolectricTestRunner::class)
class DraftSaverTest {

    @Test
    fun `luonnos palautuu identtisena`() {
        val draft = DayProgram(
            "d1", "Iltatoimet äö",
            listOf(
                ProgramStep("s1", "Pukeutuminen", "builtin:dress", "Yöpuku", StepMode.MANUAL, 5, "20:00",
                    "/v.m4a", true, listOf("Paita", "Housut")),
                ProgramStep("s2", "Nukkumaan", "/kuva.image", "", StepMode.CLOCK, 1, "21:15")
            )
        )

        assertEquals(draft, parseDraft(serializeDraft(draft)))
    }

    @Test
    fun `tyhja luonnos palautuu`() {
        val draft = DayProgram("d2", "", emptyList())

        assertEquals(draft, parseDraft(serializeDraft(draft)))
    }

    @Test
    fun `rikkinainen luonnos palauttaa tyhjan uuden luonnoksen eika kaadu`() {
        val restored = parseDraft("{ rikki")

        assertEquals("", restored.name)
        assertTrue(restored.steps.isEmpty())
        assertTrue(restored.id.isNotBlank())
    }
}
