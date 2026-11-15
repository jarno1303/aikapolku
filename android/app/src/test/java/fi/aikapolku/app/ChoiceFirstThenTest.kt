package fi.aikapolku.app

import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.firstthen.FirstThenCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sitten-kortin valinta. Tavallinen JVM-testi (ei Robolectricia): tilamuuttujien kirjoitus
 * Robolectric-ympäristössä koostumuksen ulkopuolella jättää Composen odottamaan, jolloin
 * myöhemmät käyttöliittymätestit eivät pääse lepotilaan (AppNotIdleException).
 */
class ChoiceFirstThenTest {
    @Test
    fun `Sitten-kortin valinta ja alkuperainen pari takaisin`() {
        val cards = FirstThenCards(FirstThenSelection(firstName = "Askartelu", nextName = "Lounas", nextAltName = "Välipala", nextAltPhoto = "builtin:snack"))
        assertTrue(cards.hasChoice)
        cards.chooseNext(useAlternative = true)
        assertEquals("Välipala", cards.selection.nextName)
        assertEquals("", cards.selection.nextAltName)
        assertTrue(cards.promoteNext())
        assertEquals("Välipala", cards.selection.firstName)
        cards.finishFlow()
        assertEquals("Lounas", cards.selection.nextName)
        assertEquals("Välipala", cards.selection.nextAltName)
    }

}
