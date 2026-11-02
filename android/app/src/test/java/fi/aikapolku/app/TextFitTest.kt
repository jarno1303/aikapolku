package fi.aikapolku.app

import fi.aikapolku.app.ui.TextFit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kortin nimi ei saa katketa kesken sanan (puhelintesti: "Pukeutumine / n"). */
class TextFitTest {
    @Test
    fun `sana katkeaa kesken`() {
        assertTrue(TextFit.breaksWord("Pukeutuminen", listOf(11)))
    }

    @Test
    fun `rivinvaihto sanavalissa on kunnossa`() {
        assertFalse(TextFit.breaksWord("Hampaiden pesu", listOf(10)))
        assertFalse(TextFit.breaksWord("Aamupala", emptyList()))
    }

    @Test
    fun `tavuviiva saa katkaista`() {
        assertFalse(TextFit.breaksWord("Ensin-kortti", listOf(6)))
    }
}
