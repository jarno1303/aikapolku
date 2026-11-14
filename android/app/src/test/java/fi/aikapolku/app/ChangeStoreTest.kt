package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Muutosten tallennus: kentät säilyvät, edelliset valinnat muistetaan, profiilikohtainen. */
@RunWith(RobolectricTestRunner::class)
class ChangeStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val change = EventChange(
        id = "c1",
        date = LocalDate.now(),
        eventId = "e1",
        type = ChangeType.REPLACE,
        kind = EventKind.PROGRAM,
        programId = "p1",
        reason = "Uimahalli on kiinni",
        voicePath = "/data/voice_1.m4a",
        autoPlayVoice = true,
        reveal = ChangeReveal.AT_EVENT,
        showOriginal = false
    )

    @Test
    fun `muutos sailyy kaikkine kenttineen`() {
        ChangeStore(context, "default").upsert(change)
        assertEquals(listOf(change), ChangeStore(context, "default").load())
    }

    @Test
    fun `ilta-ennakko ja muistutus sailyvat`() {
        val evening = change.copy(id = "c2", reveal = ChangeReveal.EVENING, remind = true)
        ChangeStore(context, "default").upsert(evening)
        val loaded = ChangeStore(context, "default").load().single { it.id == "c2" }
        assertEquals(ChangeReveal.EVENING, loaded.reveal)
        assertTrue(loaded.remind)
    }

    @Test
    fun `vanha tallenne ilman muistutusta lukeutuu oletuksella`() {
        val old = """[{"id":"v1","date":"2026-11-02","eventId":"e1","type":"cancel","reveal":"morning"}]"""
        val loaded = ChangeStore.decode(old).single()
        assertFalse(loaded.remind)
        assertEquals(ChangeReveal.MORNING, loaded.reveal)
    }

    @Test
    fun `edellisen muutoksen valinnat ovat valmiina`() {
        val store = ChangeStore(context, "default")
        store.upsert(change)
        assertEquals(ChangeReveal.AT_EVENT, store.lastReveal)
        assertFalse(store.lastShowOriginal)
    }

    @Test
    fun `muutokset ovat profiilikohtaisia ja kuuluvat profiilin tietoihin`() {
        ChangeStore(context, "default").upsert(change)
        assertTrue(ChangeStore(context, "toinen").load().isEmpty())
        assertTrue(ProfileScope.CHANGES in ProfileScope.SCOPED)
    }

    @Test
    fun `rikkinainen tieto ei kaada`() {
        assertTrue(ChangeStore.decode("ei json").isEmpty())
        assertTrue(ChangeStore.decode("""[{"id":"x"}]""").isEmpty())
    }
}
