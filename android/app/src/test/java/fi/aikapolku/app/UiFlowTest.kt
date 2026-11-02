package fi.aikapolku.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.TimeMode
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.programs.editor.ProgramEditorScreen
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.SupportMessageScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Käyttöliittymätestit ilman laitetta (Robolectric + Compose-testit).
 * Tekstit haetaan resursseista, joten testit toimivat myös käännetyillä kielillä.
 */
@RunWith(RobolectricTestRunner::class)
class UiFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun `uusi paivaohjelma tehdaan napauttamalla kortteja ja tallennetaan kellonajoilla`() {
        var saved: DayProgram? = null
        compose.setContent {
            var draft by remember { mutableStateOf(DayProgram("d", "", emptyList(), timeMode = TimeMode.FREE)) }
            ProgramEditorScreen(
                draft = draft,
                onDraftChange = { draft = it },
                otherProgramNames = emptyList(),
                onSave = { saved = it },
                onCancel = {}
            )
        }

        // 1 Valitse: napautusjärjestys on ohjelman järjestys.
        compose.onNodeWithContentDescription(t(R.string.card_breakfast)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(t(R.string.card_dress)).performScrollTo().performClick()
        compose.onNodeWithText(t(R.string.editor_next_count, 2)).performClick()

        // 2 Järjestä: molemmat näkyvät oikeassa järjestyksessä.
        compose.onNodeWithText(t(R.string.card_breakfast)).assertExists()
        compose.onNodeWithText(t(R.string.card_dress)).assertExists()
        compose.onNodeWithText(t(R.string.editor_next)).performClick()

        // 3 Ajat: kellonajat.
        compose.onNodeWithText(t(R.string.time_mode_clock)).performClick()
        compose.onNodeWithText(t(R.string.editor_next)).performClick()

        // 4 Tallenna: nimi ehdotetaan valmiiksi (oletusaloitus 08:00 = aamu).
        compose.onNodeWithText(t(R.string.name_suggestion_morning)).assertExists()
        compose.onAllNodes(hasText(t(R.string.save)) and hasClickAction() and isEnabled()).onLast().performClick()

        assertNotNull(saved)
        val program = saved!!
        assertEquals(listOf(t(R.string.card_breakfast), t(R.string.card_dress)), program.steps.map { it.name })
        assertEquals(TimeMode.CLOCK, program.timeMode)
        assertEquals(t(R.string.name_suggestion_morning), program.name)
        assertEquals("pukeutumisen oletusosavaiheet", 5, program.steps[1].subSteps.size)
    }

    @Test
    fun `suoritusnakyman apu tauko ja valmis ovat painettavissa`() {
        var help = 0
        var pause = 0
        var done = 0
        compose.setContent {
            RunStepScreen(
                taskName = t(R.string.card_dress),
                taskPhoto = "builtin:dress",
                description = "",
                subSteps = listOf(t(R.string.substep_socks), t(R.string.substep_trousers)),
                subStepIndex = 0,
                taskIndex = 0,
                taskCount = 3,
                time = null,
                next = null,
                nextMode = RunPreferences.NEXT_CARD,
                onToggleTimer = {},
                onHelp = { help++ },
                onBreak = { pause++ },
                onDone = { done++ },
                onMore = {}
            )
        }
        compose.onNodeWithText(t(R.string.substep_socks)).assertExists()
        compose.onNodeWithText(t(R.string.support_help), substring = true).performClick()
        compose.onNodeWithText(t(R.string.support_break), substring = true).performClick()
        compose.onNodeWithText(t(R.string.run_done_button), substring = true).performClick()
        assertEquals(listOf(1, 1, 1), listOf(help, pause, done))
    }

    @Test
    fun `taukoviesti nayttaa viestin ja jatka palaa ohjelmaan`() {
        var continued = false
        compose.setContent {
            SupportMessageScreen(
                kind = SupportKind.BREAK,
                timerPaused = true,
                onContinue = { continued = true },
                onClose = {}
            )
        }
        compose.onAllNodesWithText(t(R.string.support_break))[0].assertExists()
        compose.onNodeWithText(t(R.string.support_continue)).performClick()
        assertTrue(continued)
    }

    @Test
    fun `kuva vaihtuu kun kortti vaihtuu samassa nakymassa`() {
        // Puhelintesti 1.11.2026: Ensin–Sitten-kortin kuva vaihtui vasta näkymästä poistuttaessa.
        // Molemmat kuvat ladataan ensin välimuistiin, jotta testi ei riipu taustasäikeestä.
        val cache = fi.aikapolku.app.cards.CardImageCache
        val firstRes = fi.aikapolku.app.cards.CardArt.templates[0].drawable
        val secondRes = fi.aikapolku.app.cards.CardArt.templates[1].drawable
        val firstImage = kotlinx.coroutines.runBlocking { cache.load(context, null, firstRes) }
        val secondImage = kotlinx.coroutines.runBlocking { cache.load(context, null, secondRes) }
        org.junit.Assume.assumeTrue("kuvien purku ei käytettävissä tässä ympäristössä",
            firstImage != null && secondImage != null && firstImage !== secondImage)

        var res by mutableStateOf(firstRes)
        var shown: androidx.compose.ui.graphics.ImageBitmap? = null
        compose.setContent {
            shown = fi.aikapolku.app.cards.rememberCachedImage(null, res).image
        }
        compose.waitForIdle()
        assertTrue("ensimmäinen kuva näkyy", shown === firstImage)

        res = secondRes
        compose.waitForIdle()
        assertTrue("kuva vaihtuu heti eikä vanha jää näkyviin", shown === secondImage)
    }
}
