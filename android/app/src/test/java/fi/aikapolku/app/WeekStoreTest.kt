package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Viikko-ohjelman tallennus: tiedot säilyvät ja ovat profiilikohtaisia. */
@RunWith(RobolectricTestRunner::class)
class WeekStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val plan = WeekPlan(
        events = listOf(
            WeekEvent("1", 1, 0, "07:00", EventKind.PROGRAM, programId = "aamu"),
            WeekEvent("2", 5, 1, "12:00", EventKind.CARD, cardName = "Lääkkeet äöå", cardPhoto = "builtin:rest")
        ),
        alternating = true,
        anchorMonday = 20_394L
    )

    @Test
    fun `viikko-ohjelma sailyy kaikkine kenttineen`() {
        WeekStore(context).save(plan)
        assertEquals(plan, WeekStore(context).load())
    }

    @Test
    fun `tyhja ja rikkinainen tallennus palauttaa tyhjan viikon`() {
        assertEquals(WeekPlan(), WeekStore(context).load())
        assertEquals(WeekPlan(), WeekStore.decode("{rikki"))
    }

    @Test
    fun `virheelliset tapahtumat ohitetaan`() {
        val text = """{"events":[{"id":"x","day":9,"time":"08:00"},{"id":"","day":1},{"id":"ok","day":2,"time":"8:5","kind":"outo"}]}"""
        val loaded = WeekStore.decode(text).events
        assertEquals(listOf("ok"), loaded.map { it.id })
        assertEquals("08:05", loaded.first().time)
        assertEquals(EventKind.PROGRAM, loaded.first().kind)
    }

    @Test
    fun `viikko-ohjelma on profiilikohtainen`() {
        WeekStore(context).save(plan)
        val other = ProfileStore.add(context, "Sanna")
        ProfileStore.switchTo(context, other.id)
        assertEquals(WeekPlan(), WeekStore(context).load())
        ProfileStore.switchTo(context, ProfileScope.DEFAULT_ID)
        assertEquals(plan, WeekStore(context).load())
    }
}
