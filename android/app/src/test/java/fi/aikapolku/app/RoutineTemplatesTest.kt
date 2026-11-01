package fi.aikapolku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valmiit rutiinipohjat, oletuskortit ja osavaiheiden oletukset. Puhdas JVM-testi. */
class RoutineTemplatesTest {

    private val cardIds = AikaCardArt.templates.map { it.id }.toSet()

    @Test
    fun `pohjia on nelja oikeilla nimilla`() {
        assertEquals(
            listOf("Aamutoimet", "Iltatoimet", "Koulupäivä", "Päivätoiminta"),
            P020RoutineTemplates.all.map { it.name }
        )
    }

    @Test
    fun `jokainen pohjan vaihe viittaa olemassa olevaan korttiin`() {
        P020RoutineTemplates.all.forEach { template ->
            template.steps.forEach { step ->
                assertTrue("${template.name}: tuntematon kortti ${step.cardId}", step.cardId in cardIds)
            }
        }
    }

    @Test
    fun `jokaisesta pohjasta syntyy ohjelma kaatumatta`() {
        P020RoutineTemplates.all.forEach { template ->
            val program = P020RoutineTemplates.create(template)
            assertEquals(template.name, program.name)
            assertEquals(template.steps.size, program.steps.size)
        }
    }

    @Test
    fun `pohjan vaiheet saavat kortin nimen ja valmiin kuvan`() {
        val aamu = P020RoutineTemplates.create(P020RoutineTemplates.all.first { it.name == "Aamutoimet" })

        assertEquals(listOf("Aamupala", "Hampaiden pesu", "Pukeutuminen", "Lähtö"), aamu.steps.map { it.name })
        assertEquals("builtin:breakfast", aamu.steps[0].photo)
    }

    @Test
    fun `kestolliset vaiheet ovat kestotilassa ja muut kasin edettavia`() {
        val aamu = P020RoutineTemplates.create(P020RoutineTemplates.all.first { it.name == "Aamutoimet" })

        assertEquals("duration", aamu.steps[0].mode)
        assertEquals(15, aamu.steps[0].minutes)
        assertEquals("manual", aamu.steps.last().mode) // Lähtö: ei kestoa
    }

    @Test
    fun `pukeutuminen ja hampaiden pesu saavat oletusosavaiheet`() {
        val aamu = P020RoutineTemplates.create(P020RoutineTemplates.all.first { it.name == "Aamutoimet" })

        assertEquals(listOf("Sukat", "Housut", "Paita", "Takki", "Kengät"),
            aamu.steps.first { it.name == "Pukeutuminen" }.subSteps)
        assertEquals(3, aamu.steps.first { it.name == "Hampaiden pesu" }.subSteps.size)
        assertTrue(aamu.steps.first { it.name == "Aamupala" }.subSteps.isEmpty())
    }

    @Test
    fun `jokainen luotu ohjelma ja vaihe saa yksilollisen id-n`() {
        val template = P020RoutineTemplates.all.first()
        val a = P020RoutineTemplates.create(template)
        val b = P020RoutineTemplates.create(template)

        assertTrue(a.id != b.id)
        val stepIds = (a.steps + b.steps).map { it.id }
        assertEquals(stepIds.size, stepIds.toSet().size)
    }

    @Test
    fun `osavaiheiden oletus ei valita kirjainkoosta eika valilyonneista`() {
        assertEquals(5, P021SubstepDefaults.forCard("  PUKEUTUMINEN ").size)
        assertEquals(3, P021SubstepDefaults.forCard("hampaiden pesu").size)
        assertTrue(P021SubstepDefaults.forCard("Ulkoilu").isEmpty())
    }

    @Test
    fun `oletuskortteja on 21 ja tunnisteet ovat yksilolliset`() {
        assertEquals(21, AikaCardArt.templates.size)
        assertEquals(21, cardIds.size)
    }

    @Test
    fun `kortin kuva loytyy nimella tunnisteella ja vanhoilla nimilla`() {
        val breakfast = AikaCardArt.templates.first { it.id == "breakfast" }.drawable
        assertEquals(breakfast, AikaCardArt.imageRes("Aamupala"))
        assertEquals(breakfast, AikaCardArt.imageRes("mikä tahansa", "builtin:breakfast"))

        // Vanhojen versioiden nimet toimivat yhä
        assertNotNull(AikaCardArt.imageRes("Tehtävät"))
        assertNotNull(AikaCardArt.imageRes("Aamupiiri"))
        assertNotNull(AikaCardArt.imageRes("Nukkuminen"))
    }

    @Test
    fun `tuntematon kortti ei loyda kuvaa`() {
        assertNull(AikaCardArt.imageRes("Uimahalli"))
    }
}
