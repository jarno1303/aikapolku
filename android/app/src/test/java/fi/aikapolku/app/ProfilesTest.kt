package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.RunPreferences
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Käyttäjäprofiilit: vanha data kuuluu ensimmäiseen profiiliin, profiilien tiedot pysyvät
 * erillään ja poisto poistaa vain poistettavan profiilin tiedot.
 */
@RunWith(RobolectricTestRunner::class)
class ProfilesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun program(id: String, name: String) = DayProgram(id, name, emptyList())

    @Test
    fun `aluksi on yksi oletusprofiili ja vanha data kuuluu siihen`() {
        // Vanhan version tallennus samaan tiedostoon kuin ennen profiileja.
        ProgramStore(context).save(program("vanha", "Aamu"))
        assertEquals(listOf(ProfileScope.DEFAULT_ID), ProfileStore.list(context).map { it.id })
        assertEquals(ProfileScope.DEFAULT_ID, ProfileStore.currentId(context))
        assertEquals(
            listOf("vanha"),
            ProgramStore(context).load().map { it.id }
        )
        assertTrue(
            context.getSharedPreferences("aikapolku_programs_v1", Context.MODE_PRIVATE)
                .getString("programs", "")!!.contains("vanha")
        )
    }

    @Test
    fun `profiilien ohjelmat kortit ja asetukset pysyvat erillaan`() {
        ProgramStore(context).save(program("a", "Mikon aamu"))
        RunPreferences.setNextMode(context, RunPreferences.NEXT_HIDDEN)
        val sanna = ProfileStore.add(context, "Sanna")

        ProfileStore.switchTo(context, sanna.id)
        assertEquals(emptyList<DayProgram>(), ProgramStore(context).load())
        assertEquals(RunPreferences.NEXT_CARD, RunPreferences.nextMode(context))
        ProgramStore(context).save(program("b", "Sannan aamu"))
        CardStore(context).upsert(CardItem("k", "Uinti", "", ""))

        ProfileStore.switchTo(context, ProfileScope.DEFAULT_ID)
        assertEquals(listOf("a"), ProgramStore(context).load().map { it.id })
        assertEquals(RunPreferences.NEXT_HIDDEN, RunPreferences.nextMode(context))
        assertTrue(CardStore(context).loadCards().isEmpty())

        ProfileStore.switchTo(context, sanna.id)
        assertEquals(listOf("b"), ProgramStore(context).load().map { it.id })
    }

    @Test
    fun `profiilin valinta on oletuksena ohjaajan takana`() {
        assertFalse(ProfileStore.selfSelect(context))
        ProfileStore.setSelfSelect(context, true)
        assertTrue(ProfileStore.selfSelect(context))
    }

    @Test
    fun `viimeista profiilia ei voi poistaa`() {
        assertFalse(ProfileStore.delete(context, ProfileScope.DEFAULT_ID))
        assertEquals(1, ProfileStore.list(context).size)
    }

    @Test
    fun `poisto poistaa profiilin tiedot ja omat kuvat mutta ei muiden`() {
        val folder = File(context.filesDir, "activity_images").apply { mkdirs() }
        val shared = File(folder, "yhteinen.image").apply { writeText("x") }
        val own = File(folder, "oma.image").apply { writeText("x") }
        CardStore(context).upsert(CardItem("k1", "Kortti", "", shared.absolutePath))

        val sanna = ProfileStore.add(context, "Sanna", own.absolutePath)
        ProfileStore.switchTo(context, sanna.id)
        ProgramStore(context).save(program("s", "Sannan aamu"))
        CardStore(context).upsert(CardItem("k2", "Kortti", "", shared.absolutePath))

        assertTrue(ProfileStore.delete(context, sanna.id))

        assertEquals(listOf(ProfileScope.DEFAULT_ID), ProfileStore.list(context).map { it.id })
        assertEquals(ProfileScope.DEFAULT_ID, ProfileStore.currentId(context))
        assertFalse("profiilin oma kuva poistetaan", own.exists())
        assertTrue("toisen profiilin käyttämä kuva säilyy", shared.exists())
        // Samalla tunnuksella luotu uusi profiili ei perisi vanhoja tietoja.
        assertTrue(
            ProfileScope.prefs(context, ProfileScope.PROGRAMS, sanna.id).all.isEmpty()
        )
        assertEquals(1, CardStore(context).loadCards().size)
    }

    @Test
    fun `profiilin nimi ja kuva paivittyvat`() {
        val p = ProfileStore.add(context, "  Mikko ")
        assertEquals("Mikko", p.name)
        ProfileStore.update(context, p.copy(name = "Mikko K", photo = "/x.jpg"))
        val saved = ProfileStore.list(context).first { it.id == p.id }
        assertEquals("Mikko K", saved.name)
        assertEquals("/x.jpg", saved.photo)
    }

    @Test
    fun `rikkinainen profiililuettelo ei kaada sovellusta`() {
        assertEquals(emptyList<Any>(), ProfileStore.decode("{rikki"))
        assertEquals(1, ProfileStore.list(context).size)
    }
}
