package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.SubstepDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Valmiit rutiinipohjat, oletuskortit ja osavaiheiden oletukset (suomenkieliset resurssit). */
@RunWith(RobolectricTestRunner::class)
class RoutineTemplatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val texts: (Int) -> String = { context.getString(it) }
    private fun aamu() = RoutineTemplates.create(RoutineTemplates.all.first { texts(it.nameRes) == "Aamutoimet" }, texts)

    private val cardIds = CardArt.templates.map { it.id }.toSet()

    @Test
    fun `pohjia on nelja oikeilla nimilla`() {
        assertEquals(
            listOf("Aamutoimet", "Iltatoimet", "Koulupäivä", "Päivätoiminta"),
            RoutineTemplates.all.map { texts(it.nameRes) }
        )
    }

    @Test
    fun `jokainen pohjan vaihe viittaa olemassa olevaan korttiin`() {
        RoutineTemplates.all.forEach { template ->
            template.steps.forEach { step ->
                assertTrue("${texts(template.nameRes)}: tuntematon kortti ${step.cardId}", step.cardId in cardIds)
            }
        }
    }

    @Test
    fun `jokaisesta pohjasta syntyy ohjelma kaatumatta`() {
        RoutineTemplates.all.forEach { template ->
            val program = RoutineTemplates.create(template, texts)
            assertEquals(texts(template.nameRes), program.name)
            assertEquals(template.steps.size, program.steps.size)
        }
    }

    @Test
    fun `pohjan vaiheet saavat kortin nimen ja valmiin kuvan`() {
        val aamu = aamu()

        assertEquals(listOf("Aamupala", "Hampaiden pesu", "Pukeutuminen", "Lähtö"), aamu.steps.map { it.name })
        assertEquals("builtin:breakfast", aamu.steps[0].photo)
    }

    @Test
    fun `kestolliset vaiheet ovat kestotilassa ja muut kasin edettavia`() {
        val aamu = aamu()

        assertEquals(StepMode.DURATION, aamu.steps[0].mode)
        assertEquals(15, aamu.steps[0].minutes)
        assertEquals(StepMode.MANUAL, aamu.steps.last().mode) // Lähtö: ei kestoa
    }

    @Test
    fun `pukeutuminen ja hampaiden pesu saavat oletusosavaiheet`() {
        val aamu = aamu()

        assertEquals(listOf("Sukat", "Housut", "Paita", "Takki", "Kengät"),
            aamu.steps.first { it.name == "Pukeutuminen" }.subSteps)
        assertEquals(3, aamu.steps.first { it.name == "Hampaiden pesu" }.subSteps.size)
        assertTrue(aamu.steps.first { it.name == "Aamupala" }.subSteps.isEmpty())
    }

    @Test
    fun `jokainen luotu ohjelma ja vaihe saa yksilollisen id-n`() {
        val template = RoutineTemplates.all.first()
        val a = RoutineTemplates.create(template, texts)
        val b = RoutineTemplates.create(template, texts)

        assertTrue(a.id != b.id)
        val stepIds = (a.steps + b.steps).map { it.id }
        assertEquals(stepIds.size, stepIds.toSet().size)
    }

    @Test
    fun `osavaiheiden oletus ei valita kirjainkoosta eika valilyonneista`() {
        assertEquals(5, SubstepDefaults.forCard("  PUKEUTUMINEN ", texts).size)
        assertEquals(3, SubstepDefaults.forCard("hampaiden pesu", texts).size)
        assertTrue(SubstepDefaults.forCard("Ulkoilu", texts).isEmpty())
    }

    @Test
    fun `oletuskortteja on 25 ja tunnisteet ovat yksilolliset`() {
        assertEquals(25, CardArt.templates.size)
        assertEquals(25, cardIds.size)
    }

    @Test
    fun `kortin kuva loytyy nimella tunnisteella ja vanhoilla nimilla`() {
        val breakfast = CardArt.templates.first { it.id == "breakfast" }.drawable
        assertEquals(breakfast, CardArt.imageRes("Aamupala"))
        assertEquals(breakfast, CardArt.imageRes("mikä tahansa", "builtin:breakfast"))

        // Vanhojen versioiden nimet toimivat yhä
        assertNotNull(CardArt.imageRes("Tehtävät"))
        assertNotNull(CardArt.imageRes("Aamupiiri"))
        assertNotNull(CardArt.imageRes("Nukkuminen"))
    }

    @Test
    fun `tuntematon kortti ei loyda kuvaa`() {
        assertNull(CardArt.imageRes("Uimahalli"))
    }

    @Test
    fun `kaannetty nimi tunnistetaan ja vanha suomenkielinen nimi toimii aina`() {
        // Kuvitteellinen käännös: toisella kielellä luotu kortti löytää saman kuvan.
        // (Nimi valittu niin, ettei se ole sama kuin kortin tunnus "breakfast".)
        val translated: (Int) -> String = { if (it == R.string.card_breakfast) "Morning meal" else "?" }
        val breakfast = CardArt.templates.first { it.id == "breakfast" }.drawable
        assertEquals(breakfast, CardArt.imageRes("Morning meal", "", translated))
        assertEquals(breakfast, CardArt.imageRes("Aamupala", "", translated))
        assertNull("ilman käännöstä nimeä ei tunneta", CardArt.imageRes("Morning meal"))
    }

    @Test
    fun `valmiiden korttien nimet tulevat resursseista`() {
        assertEquals("Aamupala", texts(CardArt.templates.first { it.id == "breakfast" }.nameRes))
        assertEquals(25, CardArt.templates.map { texts(it.nameRes) }.toSet().size)
    }
}
