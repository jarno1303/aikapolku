package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.Category
import fi.aikapolku.app.data.CategoryStore
import fi.aikapolku.app.backup.BackupArchive
import fi.aikapolku.app.backup.BackupFormat
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.link.ResidentPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Värikoodaus: valmiiden korttien oletusluokat, ohjaajan valinnat, nimet ja siirto asukkaan puhelimeen. */
@RunWith(RobolectricTestRunner::class)
class VarikoodausTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val texts: (Int) -> String = { context.getString(it) }

    private fun resolve(name: String, photo: String = "", title: String? = null) =
        CategoryStore.resolve(context, name, photo, title, texts)

    @Test
    fun `valmiilla korteilla on oletusluokka nimen tai kuvan mukaan`() {
        assertEquals(Category.MEAL, resolve("Lounas"))
        assertEquals(Category.HYGIENE, resolve("Hampaiden pesu"))
        assertEquals(Category.OUTDOOR, resolve("", "builtin:outdoors"))
        // Oma kortti valmiilla kuvalla saa kuvan luokan.
        assertEquals(Category.MEAL, resolve("Ainon aamiainen", "builtin:breakfast"))
        // Ei oletusta: oma kortti omalla kuvalla, Lähtö ja Odota.
        assertNull(resolve("Sauna", "/data/kuva.jpg"))
        assertNull(resolve("Lähtö"))
        assertNull(resolve("Odota"))
    }

    @Test
    fun `ohjaajan valinta koskee korttia nimen mukaan eika valmista kuvaa`() {
        val key = CategoryStore.key("Ainon aamiainen", "builtin:breakfast", texts)
        assertEquals("n:ainon aamiainen", key)
        CategoryStore.set(context, key, Category.HEALTH)
        assertEquals(Category.HEALTH, resolve("Ainon aamiainen", "builtin:breakfast"))
        // Valmis Aamupala ei muuttunut.
        assertEquals(Category.MEAL, resolve("Aamupala", "builtin:breakfast"))
        // "Ei luokkaa" voittaa oletuksen.
        CategoryStore.set(context, CategoryStore.key("Lounas", "", texts), null)
        assertNull(resolve("Lounas"))
        assertNull(resolve("lounas", "builtin:lunch_meal"))
    }

    @Test
    fun `samanniminen kortti ja ohjelma eivat sekoitu ja nimen vaihto siirtaa luokan`() {
        CategoryStore.set(context, CategoryStore.programKey("Lounas"), Category.ACTIVITY)
        assertEquals(Category.ACTIVITY, resolve("Lounas", "builtin:lunch_meal", title = "Lounas"))
        assertEquals(Category.MEAL, resolve("Lounas", "builtin:lunch_meal"))
        CategoryStore.set(context, CategoryStore.key("Kauppareissu", "", texts), Category.OUTDOOR)
        CategoryStore.rekey(context, CategoryStore.key("Kauppareissu", "", texts), CategoryStore.key("Kauppa", "", texts))
        assertEquals(Category.OUTDOOR, resolve("Kauppa"))
    }

    @Test
    fun `paivaohjelman oma luokka voittaa ensimmaisen kortin luokan`() {
        assertEquals(Category.MEAL, resolve("Aamupala", "builtin:breakfast", title = "Aamutoimet"))
        CategoryStore.set(context, CategoryStore.programKey("Aamutoimet"), Category.HYGIENE)
        assertEquals(Category.HYGIENE, resolve("Aamupala", "builtin:breakfast", title = "Aamutoimet"))
        // Sama kortti ilman ohjelmaa pysyy ruokailuna.
        assertEquals(Category.MEAL, resolve("Aamupala", "builtin:breakfast"))
    }

    @Test
    fun `nimet ja asukaskohtainen paalla-asetus`() {
        assertEquals("Ruokailu", CategoryStore.name(context, Category.MEAL))
        CategoryStore.rename(context, Category.MEAL, "  Ateriat  ")
        assertEquals("Ateriat", CategoryStore.name(context, Category.MEAL))
        CategoryStore.rename(context, Category.MEAL, "")
        assertEquals("Ruokailu", CategoryStore.name(context, Category.MEAL))

        val aino = ProfileStore.add(context, "Aino").id
        val eero = ProfileStore.add(context, "Eero").id
        ProfileStore.switchTo(context, aino)
        assertTrue(CategoryStore.enabled(context))
        CategoryStore.setEnabled(context, false)
        assertFalse(CategoryStore.enabled(context, aino))
        assertTrue("Toisen asukkaan asetus ei muutu", CategoryStore.enabled(context, eero))
    }

    @Test
    fun `luokat siirtyvat asukkaan puhelimeen ja muuttavat paketin version`() {
        val aino = ProfileStore.add(context, "Aino").id
        WeekStore(context, aino).save(WeekPlan(listOf(
            WeekEvent("s", 1, 0, "18:00", EventKind.CARD, cardName = "Sauna"),
            WeekEvent("k", 2, 0, "10:00", EventKind.CARD, cardName = "Kauppa")
        )))
        CategoryStore.set(context, CategoryStore.key("Sauna", "", texts), Category.HYGIENE)
        // Toisen asukkaan oma kortti ei kuulu Ainon pakettiin (tietosuoja).
        CategoryStore.set(context, CategoryStore.key("Eeron lääke", "", texts), Category.HEALTH)
        CategoryStore.rename(context, Category.ACTIVITY, "Työtoiminta")
        val before = ResidentPackage.version(context, aino)
        val bytes = ResidentPackage.build(context, aino).bytes
        val data = org.json.JSONObject(String(BackupArchive.unzip(bytes).getValue(BackupFormat.DATA), Charsets.UTF_8))
        val sent = data.getJSONObject("categories").getJSONObject("map")
        assertTrue(sent.has("n:sauna"))
        assertFalse("Muiden asukkaiden kortit eivät lähde", sent.has("n:eeron lääke"))

        // Asukkaan puhelimessa oma valinta toiselle kortille säilyy, paikan valinnat voittavat.
        CategoryStore.set(context, CategoryStore.key("Sauna", "", texts), null)
        CategoryStore.set(context, CategoryStore.key("Kauppa", "", texts), Category.OUTDOOR)
        CategoryStore.rename(context, Category.ACTIVITY, "")
        assertNotEquals("Luokan muutos muuttaa version", before, ResidentPackage.version(context, aino))

        ResidentPackage.apply(context, bytes, placeId = "paikka", localId = aino)
        assertEquals(Category.HYGIENE, resolve("Sauna"))
        assertEquals(Category.OUTDOOR, resolve("Kauppa"))
        assertEquals("Työtoiminta", CategoryStore.name(context, Category.ACTIVITY))
    }
}
