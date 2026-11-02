package fi.aikapolku.app

import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.DayParts
import fi.aikapolku.app.data.DayTime
import fi.aikapolku.app.data.TimelineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Asiakkaan etusivu: oletukset, päivän osat ja aikajanan rivit. Puhdas JVM-testi. */
class ClientViewTest {
    @Test
    fun `oletuksena koko paivan aikajana kellonajoin ja tyokalut piilossa`() {
        val p = ClientViewPrefs()
        assertEquals(TimelineMode.FULL, p.timeline)
        assertEquals(true, p.showTimes)
        assertFalse(p.anyTool)
        assertEquals(TimelineMode.FULL, TimelineMode.from("tuntematon"))
        assertEquals(TimelineMode.NEXT, TimelineMode.from("next"))
    }

    @Test
    fun `paivan osat aamu paiva ilta`() {
        assertEquals(DayTime.MORNING, DayParts.of("07:30"))
        assertEquals(DayTime.MORNING, DayParts.of("10:59"))
        assertEquals(DayTime.DAY, DayParts.of("11:00"))
        assertEquals(DayTime.DAY, DayParts.of("16:59"))
        assertEquals(DayTime.EVENING, DayParts.of("17:00"))
    }

    @Test
    fun `aikajanan rivit asetuksen mukaan`() {
        val skipped = setOf(3)
        assertEquals((0 until 6).toList(), DayParts.visible(6, 2, { it in skipped }, TimelineMode.FULL))
        assertEquals(listOf(2, 4, 5), DayParts.visible(6, 2, { it in skipped }, TimelineMode.NEXT))
        assertEquals(emptyList<Int>(), DayParts.visible(6, 2, { false }, TimelineMode.NONE))
        assertEquals(emptyList<Int>(), DayParts.visible(6, -1, { false }, TimelineMode.NEXT))
    }
}
