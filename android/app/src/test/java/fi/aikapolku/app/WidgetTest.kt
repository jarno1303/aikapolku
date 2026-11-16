package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.widget.WidgetArt
import fi.aikapolku.app.widget.WidgetDay
import fi.aikapolku.app.widget.WidgetState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Kotinäytön widget: Nyt, Seuraavaksi ja väliaika samoista tiedoista kuin Tänään-lista. */
@RunWith(RobolectricTestRunner::class)
class WidgetTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        val events = (1..7).flatMap { d ->
            listOf(
                WeekEvent("aamu$d", d, 0, "08:00", EventKind.CARD, cardName = "Aamupala", cardPhoto = "builtin:breakfast"),
                WeekEvent("lounas$d", d, 0, "12:00", EventKind.CARD, cardName = "Lounas", cardPhoto = "builtin:lunch_meal")
            )
        }
        WeekStore(context).save(WeekPlan(events))
    }

    private val d get() = today.dayOfWeek.value

    @Test
    fun `nyt seuraavaksi ja mihin asti`() {
        val c = WidgetDay.compute(context, today.atTime(9, 0))
        assertEquals(WidgetState.ACTIVE, c.state)
        assertEquals("Aamupala", c.now?.title)
        // Tavallinen tapahtuma: ei "asti"-aikaa (vain kestävällä tapahtumalla ja väliajalla).
        assertNull(c.until)
        assertEquals("08:00", c.now?.time)
        assertEquals("Lounas", c.next?.title)
        assertEquals(today.atTime(12, 0), WidgetDay.nextChange(context, today.atTime(9, 0)))
    }

    @Test
    fun `vali-aikana nyt on vali-ajan kortti ja seuraavaksi tuleva`() {
        val c = WidgetDay.compute(context, today.atTime(7, 0))
        assertEquals(WidgetState.GAP, c.state)
        assertEquals("Vapaa-aika", c.now?.title)
        assertEquals("08:00", c.until)
        assertEquals("Aamupala", c.next?.title)
    }

    @Test
    fun `tehty paiva ja pois kytketty widget`() {
        val acks = AckStore(context)
        acks.ack(today, "aamu$d", LocalTime.of(8, 30))
        acks.ack(today, "lounas$d", LocalTime.of(12, 30))
        assertEquals(WidgetState.DONE, WidgetDay.compute(context, today.atTime(13, 0)).state)
        assertEquals(today.plusDays(1).atStartOfDay().plusMinutes(1), WidgetDay.nextChange(context, today.atTime(13, 0)))

        WidgetDay.setEnabled(context, false)
        val off = WidgetDay.compute(context, today.atTime(9, 0))
        assertEquals(WidgetState.OFF, off.state)
        assertNull(off.now)
    }

    @Test
    fun `laakekortti nakyy kotinaytolla neutraalisti`() {
        fi.aikapolku.app.data.CategoryStore.set(context, fi.aikapolku.app.data.CategoryStore.key("Lounas", "") { context.getString(it) }, fi.aikapolku.app.data.Category.HEALTH)
        val c = WidgetDay.compute(context, today.atTime(9, 0))
        assertEquals("Oma asia", c.next?.title)
        assertEquals("", c.next?.imagePath)
    }

    @Test
    fun `paikan laitteella widget on aina pois`() {
        fi.aikapolku.app.link.LinkStore(context).placeEnabled = true
        assertEquals(WidgetState.OFF, WidgetDay.compute(context, today.atTime(9, 0)).state)
    }

    @Test
    fun `kortin kuva piirtyy oikean kokoisena`() {
        val c = WidgetDay.compute(context, today.atTime(9, 0))
        val bitmap = WidgetArt.render(context, c.now!!)
        assertEquals(WidgetArt.SIZE, bitmap.width)
        assertEquals(WidgetArt.SIZE, bitmap.height)
    }
}
