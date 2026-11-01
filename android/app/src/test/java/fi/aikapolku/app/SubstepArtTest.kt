package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.cards.SubstepArt
import fi.aikapolku.app.data.SubstepDefaults
import fi.aikapolku.app.data.SupportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P0-22a: osavaiheiden kuvat ja viestinäkymän kuva. */
@RunWith(RobolectricTestRunner::class)
class SubstepArtTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val texts: (Int) -> String = { context.getString(it) }

    @Test
    fun `jokaisella oletusosavaiheella on oma kuva`() {
        val defaults = SubstepDefaults.forCard("Pukeutuminen", texts) +
            SubstepDefaults.forCard("Hampaiden pesu", texts)

        assertEquals(8, defaults.size)
        defaults.forEach { name ->
            assertNotNull("Osavaiheelta '$name' puuttuu kuva", SubstepArt.imageRes(name))
        }
    }

    @Test
    fun `hampaiden pesun viimeinen osavaihe on Laita hammasharja takaisin`() {
        assertEquals(
            listOf("Ota hammasharja", "Pese hampaat", "Laita hammasharja takaisin"),
            SubstepDefaults.forCard("Hampaiden pesu", texts)
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
    fun `tauko- ja apuviestilla on omat kuvat`() {
        val tauko = SubstepArt.messageImageRes(SupportKind.BREAK)
        val apu = SubstepArt.messageImageRes(SupportKind.HELP)
        assertNotNull(tauko)
        assertNotNull(apu)
        assertNotEquals(tauko, apu)
    }

    @Test
    fun `kaannetty osavaiheen nimi loytaa kuvan`() {
        val english: (Int) -> String = { if (it == R.string.substep_socks) "Socks" else "?" }
        assertEquals(SubstepArt.imageRes("Sukat"), SubstepArt.imageRes("Socks", english))
        assertNull(SubstepArt.imageRes("Socks"))
    }
}
