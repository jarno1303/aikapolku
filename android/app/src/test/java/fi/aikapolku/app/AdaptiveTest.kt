package fi.aikapolku.app

import android.content.pm.ActivityInfo
import fi.aikapolku.app.data.ScreenOrientation
import fi.aikapolku.app.ui.Adaptive
import fi.aikapolku.app.ui.WindowSize
import fi.aikapolku.app.ui.isTwoPane
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ruudun kokoluokka ja asennon lukitus (havainto 15.11.2026). Tavallisia JVM-testejä. */
class AdaptiveTest {
    @Test
    fun `kokoluokka tunnistetaan ruudun koosta`() {
        assertEquals(WindowSize.NARROW, Adaptive.classify(411, 851))
        assertEquals(WindowSize.LOW, Adaptive.classify(851, 411))
        assertEquals(WindowSize.WIDE, Adaptive.classify(800, 1280))
        assertEquals(WindowSize.WIDE, Adaptive.classify(1280, 800))
        assertTrue(Adaptive.isTablet(600))
        assertFalse(Adaptive.isTablet(411))
    }

    @Test
    fun `kaksi palstaa vain matalalla ja levealla alueella`() {
        assertTrue(isTwoPane(827.dp, 319.dp))
        assertFalse(isTwoPane(387.dp, 723.dp))
        assertFalse(isTwoPane(696.dp, 672.dp))
    }

    @Test
    fun `asento lukitaan asiakkaalle puhelimessa ja ohjaaja on vapaa`() {
        val portrait = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        val free = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        assertEquals(portrait, ScreenOrientation.requested(ScreenOrientation.AUTO, tablet = false, caregiver = false))
        assertEquals(free, ScreenOrientation.requested(ScreenOrientation.AUTO, tablet = true, caregiver = false))
        assertEquals(free, ScreenOrientation.requested(ScreenOrientation.PORTRAIT, tablet = false, caregiver = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, ScreenOrientation.requested(ScreenOrientation.LANDSCAPE, tablet = true, caregiver = false))
        assertEquals(free, ScreenOrientation.requested(ScreenOrientation.FREE, tablet = false, caregiver = false))
    }
}
