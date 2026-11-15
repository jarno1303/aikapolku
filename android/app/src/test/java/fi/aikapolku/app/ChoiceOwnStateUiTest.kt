package fi.aikapolku.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import fi.aikapolku.app.data.ChoiceOption
import fi.aikapolku.app.data.OwnState
import fi.aikapolku.app.programs.run.ChoiceScreen
import fi.aikapolku.app.programs.run.OwnStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Valintanäkymä ja oma tila asiakkaan näkökulmasta. */
@RunWith(RobolectricTestRunner::class)
class ChoiceOwnStateUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `valinta vasta Aloita-painalluksella ja sen voi vaihtaa`() {
        var started = -1
        var none = false
        rule.setContent {
            ChoiceScreen(
                options = listOf(ChoiceOption("Ulkoilu", "builtin:outdoors"), ChoiceOption("Musiikki", "builtin:music")),
                allowNone = true,
                onStart = { started = it },
                onNone = { none = true }
            )
        }
        rule.onNodeWithText("Aloita").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Ulkoilu").performClick()
        rule.onNodeWithContentDescription("Musiikki").performClick()
        rule.onNodeWithText("Valitsit: Musiikki").assertExists()
        assertEquals("ei vielä aloitettu", -1, started)
        rule.onNodeWithText("Aloita").performClick()
        assertEquals(1, started)
        rule.onNodeWithText("En halua kumpaakaan").performClick()
        assertTrue(none)
    }

    @Test
    fun `oma tila tarjoaa tauon ja avun ja nayttaa kortin ohjaajalle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OwnState.setEnabled(context, true)
        var breakAsked = false
        rule.setContent { OwnStateFlow(onClose = {}, onBreak = { breakAsked = true }, onHelp = {}) }
        rule.onNodeWithText("Miltä nyt tuntuu?").assertExists()
        rule.onNodeWithText("Huono").performClick()
        rule.onNodeWithText("Minulla on nyt: Huono").assertExists()
        rule.onNodeWithText("Kerro ohjaajalle").assertDoesNotExist()
        rule.onNodeWithText("Tarvitsen tauon").performClick()
        assertTrue(breakAsked)
        rule.onNodeWithText("Näytä ohjaajalle").performClick()
        rule.onNodeWithText("Takaisin").assertExists()
        assertEquals(1, OwnState.today(context).size)
    }
}
