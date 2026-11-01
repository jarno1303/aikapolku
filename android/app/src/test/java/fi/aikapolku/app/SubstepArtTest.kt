package fi.aikapolku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** P0-22a: osavaiheiden kuvat ja viestinäkymän kuva. Puhdas JVM-testi. */
class SubstepArtTest {

    @Test
    fun `jokaisella oletusosavaiheella on oma kuva`() {
        val defaults = P021SubstepDefaults.forCard("Pukeutuminen") +
            P021SubstepDefaults.forCard("Hampaiden pesu")

        assertEquals(8, defaults.size)
        defaults.forEach { name ->
            assertNotNull("Osavaiheelta '$name' puuttuu kuva", SubstepArt.imageRes(name))
        }
    }

    @Test
    fun `hampaiden pesun viimeinen osavaihe on Laita hammasharja takaisin`() {
        assertEquals(
            listOf("Ota hammasharja", "Pese hampaat", "Laita hammasharja takaisin"),
            P021SubstepDefaults.forCard("Hampaiden pesu")
        )
    }

    @Test
    fun `vanhoihin ohjelmiin tallennettu nimi saa saman kuvan`() {
        assertEquals(
            SubstepArt.imageRes("Laita hammasharja takaisin"),
            SubstepArt.imageRes("Laita välineet takaisin")
        )
    }

    @Test
    fun `kuvahaku ei valita kirjainkoosta eika valilyonneista`() {
        assertEquals(SubstepArt.imageRes("Sukat"), SubstepArt.imageRes("  SUKAT "))
        assertEquals(SubstepArt.imageRes("Kengät"), SubstepArt.imageRes("kengät"))
    }

    @Test
    fun `omalle osavaiheelle ilman kuvaa kaytetaan tehtavan kuvaa`() {
        // null = suoritusnäkymä näyttää tehtävän oman kuvan (valokuva tai valmis kortti).
        assertNull(SubstepArt.imageRes("Kampaa hiukset"))
        assertNull(SubstepArt.imageRes(""))
    }

    @Test
    fun `taukoviestilla on kuva ja apuviesti kayttaa toistaiseksi kuvaketta`() {
        assertNotNull(SubstepArt.messageImageRes("Tarvitsen tauon"))
        assertNull(SubstepArt.messageImageRes("Tarvitsen apua"))
    }
}
