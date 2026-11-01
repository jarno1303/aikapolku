package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.FirstThenSelection
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Omat toimintokortit ja viimeisin Ensin–Sitten-valinta (CardStore). */
@RunWith(RobolectricTestRunner::class)
class CardStoreTest {

    private lateinit var context: Context
    private lateinit var store: CardStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CardStore(context)
    }

    private fun rawPrefs() =
        context.getSharedPreferences("aikapolku_cards_v1", Context.MODE_PRIVATE)

    private fun card(id: String, name: String = "Kortti $id") = CardItem(
        id = id,
        name = name,
        description = "Kuvaus",
        photoPath = "/data/activity_images/$id.image",
        voicePath = "/data/voice_$id.m4a",
        autoPlayVoice = true
    )

    @Test
    fun `tyhja kirjasto palauttaa tyhjan listan`() {
        assertEquals(emptyList<CardItem>(), store.loadCards())
    }

    @Test
    fun `kortti sailyy kaikkine kenttineen myos uudessa instanssissa`() {
        val c = card("c1", "Uimahalli äö")
        store.upsert(c)

        assertEquals(listOf(c), CardStore(context).loadCards())
    }

    @Test
    fun `sama id paivittaa kortin eika luo kopiota`() {
        store.upsert(card("c1", "Vanha"))
        store.upsert(card("c1", "Uusi"))

        val cards = store.loadCards()
        assertEquals(1, cards.size)
        assertEquals("Uusi", cards[0].name)
    }

    @Test
    fun `poisto poistaa vain valitun kortin`() {
        store.upsert(card("c1"))
        store.upsert(card("c2"))

        store.delete("c1")

        assertEquals(listOf("c2"), store.loadCards().map { it.id })
    }

    @Test
    fun `kortti ilman id-ta tai nimea ohitetaan latauksessa`() {
        rawPrefs().edit().putString(
            "cards",
            """[{"id":"","name":"Ei id"},{"id":"x","name":""},{"id":"ok","name":"Kelpaa"}]"""
        ).commit()

        assertEquals(listOf("ok"), CardStore(context).loadCards().map { it.id })
    }

    @Test
    fun `rikkinainen korttitallennus ei kaada sovellusta`() {
        rawPrefs().edit().putString("cards", "[[[ rikki").commit()

        assertEquals(emptyList<CardItem>(), CardStore(context).loadCards())
    }

    @Test
    fun `ensin-sitten-valinnan oletukset ovat askartelu ja ruokailu`() {
        val selection = store.loadSelection()

        assertEquals("Askartelu", selection.firstName)
        assertEquals("Ruokailu", selection.nextName)
        assertEquals(false, selection.firstVoiceAuto)
    }

    @Test
    fun `ensin-sitten-valinta sailyy`() {
        val selection = FirstThenSelection(
            firstName = "Kotitehtävät",
            nextName = "Pelaaminen",
            firstPhoto = "/a.image",
            nextPhoto = "/b.image",
            firstDescription = "Matikka",
            nextDescription = "20 min",
            firstVoice = "/a.m4a",
            nextVoice = "/b.m4a",
            firstVoiceAuto = true,
            nextVoiceAuto = false
        )
        store.saveSelection(selection)

        assertEquals(selection, CardStore(context).loadSelection())
    }

    @Test
    fun `rikkinainen valinta palauttaa oletukset`() {
        rawPrefs().edit().putString("selection", "ei json").commit()

        assertEquals("Askartelu", CardStore(context).loadSelection().firstName)
    }
}
