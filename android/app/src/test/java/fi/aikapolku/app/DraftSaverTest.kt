package fi.aikapolku.app

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
        val draft = AP16Program(
            "d1", "Iltatoimet äö",
            listOf(
                AP16Step("s1", "Pukeutuminen", "builtin:dress", "Yöpuku", "manual", 5, "20:00",
                    "/v.m4a", true, listOf("Paita", "Housut")),
                AP16Step("s2", "Nukkumaan", "/kuva.image", "", "clock", 1, "21:15")
            )
        )

        assertEquals(draft, p017ParseDraft(p017SerializeDraft(draft)))
    }

    @Test
    fun `tyhja luonnos palautuu`() {
        val draft = AP16Program("d2", "", emptyList())

        assertEquals(draft, p017ParseDraft(p017SerializeDraft(draft)))
    }

    @Test
    fun `rikkinainen luonnos palauttaa tyhjan uuden luonnoksen eika kaadu`() {
        val restored = p017ParseDraft("{ rikki")

        assertEquals("", restored.name)
        assertTrue(restored.steps.isEmpty())
        assertTrue(restored.id.isNotBlank())
    }
}
