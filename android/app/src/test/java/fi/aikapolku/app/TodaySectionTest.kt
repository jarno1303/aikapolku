package fi.aikapolku.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.week.TodaySection
import fi.aikapolku.app.week.dayName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Aloitusnäkymän Tänään-lista: näkyy vain, kun päivälle on tapahtumia, ja avaa tapahtuman. */
@RunWith(RobolectricTestRunner::class)
class TodaySectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)
    private val today = LocalDate.now().dayOfWeek.value

    @Test
    fun `tanaan-lista ei nay ilman tapahtumia`() {
        compose.setContent { Column { TodaySection(onOpenProgram = {}, onOpenCard = {}) } }
        compose.onNodeWithText(t(R.string.today_title, dayName(today))).assertDoesNotExist()
    }

    @Test
    fun `tanaan-listan kortti avautuu napauttamalla`() {
        WeekStore(context).save(
            WeekPlan(listOf(WeekEvent("e", today, 0, "00:00", EventKind.CARD, cardName = "Lääkkeet", cardPhoto = "builtin:rest")))
        )
        var opened = ""
        // Lista päivittää kellonaikaa jatkuvasti; testikello ei saa yrittää kelata sitä loppuun.
        compose.mainClock.autoAdvance = false
        compose.setContent { Column { TodaySection(onOpenProgram = {}, onOpenCard = { opened = it.cardName }) } }
        compose.onNodeWithText(t(R.string.today_title, dayName(today))).assertExists()
        compose.onNodeWithText("Lääkkeet").performClick()
        assertEquals("Lääkkeet", opened)
    }
}
