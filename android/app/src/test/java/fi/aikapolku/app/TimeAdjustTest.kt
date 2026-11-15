package fi.aikapolku.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fi.aikapolku.app.timer.TimeAdjust
import fi.aikapolku.app.timer.TimeAdjustControls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ajan asetus − / + -painikkeilla ja pikavalinnoilla (puhelintesti 15.11.2026). */
@RunWith(RobolectricTestRunner::class)
class TimeAdjustTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `askel ja pitka painallus pysyvat rajoissa`() {
        assertEquals(16, TimeAdjust.step(15, up = true))
        assertEquals(14, TimeAdjust.step(15, up = false))
        assertEquals(1, TimeAdjust.step(1, up = false))
        assertEquals(60, TimeAdjust.step(60, up = true))
        assertEquals(20, TimeAdjust.jump(15, up = true))
        assertEquals(20, TimeAdjust.jump(16, up = true))
        assertEquals(10, TimeAdjust.jump(15, up = false))
        assertEquals(15, TimeAdjust.jump(16, up = false))
        assertEquals(1, TimeAdjust.jump(3, up = false))
        assertEquals(60, TimeAdjust.jump(58, up = true))
    }

    @Test
    fun `painikkeet ja pikavalinnat muuttavat aikaa`() {
        var minutes by mutableIntStateOf(15)
        rule.setContent { TimeAdjustControls(minutes = minutes, onSet = { minutes = it }) }

        rule.onNodeWithContentDescription("Lisää aikaa").performClick()
        rule.waitForIdle()
        assertEquals(16, minutes)
        rule.onNodeWithTag(TimeAdjust.MINUTES_TAG, useUnmergedTree = true).assertTextEquals("16 min")

        rule.onNodeWithContentDescription("Vähennä aikaa").performClick()
        rule.waitForIdle()
        assertEquals(15, minutes)

        rule.onNodeWithText("30 min").performClick()
        rule.waitForIdle()
        assertEquals(30, minutes)

        minutes = 1
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Vähennä aikaa").assertIsNotEnabled()
    }
}
