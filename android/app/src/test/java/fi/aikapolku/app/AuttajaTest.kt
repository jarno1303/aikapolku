package fi.aikapolku.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import fi.aikapolku.app.data.Helper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kuka auttaa asiakasta (16.11.2026): asiakkaan viestit puhuvat ohjaajasta, läheisestä tai
 * aikuisesta ohjaajan valinnan mukaan. Oletus on ohjaaja (asumisyksikkö).
 */
@RunWith(RobolectricTestRunner::class)
class AuttajaTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `oletus on ohjaaja ja valinta taivuttaa viestit`() {
        assertEquals(Helper.CAREGIVER, Helper.current(context))
        assertEquals("Ohjaaja kertoo, kun lähdetään.", context.getString(R.string.wait_adult_tells, Helper.nominative(context)))
        assertEquals("Näytä ohjaajalle", context.getString(R.string.own_state_show_adult, Helper.to(context)))

        Helper.set(context, Helper.RELATIVE)
        assertEquals("Läheinen kertoo, kun lähdetään.", context.getString(R.string.wait_adult_tells, Helper.nominative(context)))
        assertEquals("Näytä viesti läheiselle. Aika jatkuu.", context.getString(R.string.more_time_show_adult, Helper.to(context)))
        assertEquals("Kerro läheiselle", context.getString(R.string.link_tell_helper, Helper.to(context)))

        Helper.set(context, Helper.ADULT)
        assertEquals("Näytä aikuiselle", context.getString(R.string.own_state_show_adult, Helper.to(context)))
        assertEquals(Helper.CAREGIVER, Helper.of("tuntematon"))
    }

    @Test
    fun `asiakkaan teksteissa ei ole kiintea aikuinen`() {
        val ids = listOf(
            R.string.own_state_private, R.string.own_state_show_adult, R.string.link_sent_failed,
            R.string.wait_adult_tells, R.string.wait_a_little_more, R.string.more_time_show_adult,
            R.string.link_queued, R.string.link_info, R.string.choice_allow_none_info, R.string.more_time_info
        )
        ids.forEach { id ->
            val raw = context.resources.getText(id).toString()
            assertFalse("Kiinteä 'aikui-' tekstissä: $raw", raw.contains("aikui", ignoreCase = true))
        }
    }
}
