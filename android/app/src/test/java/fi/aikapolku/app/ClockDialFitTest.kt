package fi.aikapolku.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.timer.ClockDial
import fi.aikapolku.app.timer.DialText
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Puhelintesti 15.11.2026 (JarnoL): alaspäin laskennassa numero ja teksti eivät mahtuneet
 * kellotauluun. Mitataan, että viimeisten sekuntien laskenta ja mm:ss-lukema pysyvät taulun
 * valkoisen keskustan sisällä pienimmästä suurimpaan tauluun, myös suurella fonttikoolla.
 */
@RunWith(RobolectricTestRunner::class)
class ClockDialFitTest {
    @get:Rule
    val rule = createComposeRule()

    private val sizes = listOf(150.dp, 174.dp, 212.dp, 300.dp, 420.dp)
    private val fontScales = listOf(1f, 1.3f, 2f)

    private var size by mutableStateOf(150.dp)
    private var fontScale by mutableStateOf(1f)

    /** Suorakulmion kulmat ympyrän (halkaisija [inner]) sisällä, 1 dp:n toleranssilla. */
    private fun assertInside(tag: String, inner: Dp, label: String) {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val d = node.layoutInfo.density.density
        val w = node.size.width / d
        val h = node.size.height / d
        val corner = sqrt((w / 2) * (w / 2) + (h / 2) * (h / 2))
        assertTrue("$label: ${w}x$h dp ei mahdu keskustaan ${inner.value} dp", corner <= inner.value / 2 + 1f)
    }

    @Test
    fun `viimeisten sekuntien laskenta mahtuu tauluun`() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ClockDial(remaining = 10_000L, seconds = 10, lastTen = true, onAdjustMinutes = {}, modifier = Modifier.size(size))
            }
        }
        sizes.forEach { s ->
            fontScales.forEach { f ->
                size = s
                fontScale = f
                rule.waitForIdle()
                assertInside(DialText.LAST_TEN_TAG, DialText.innerDiameter(s), "taulu ${s.value} dp, fontti $f")
            }
        }
    }

    @Test
    fun `mm ss -lukema mahtuu tauluun`() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                val density = LocalDensity.current
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    // Sama mitoitus kuin TimerScreen: koko keskustasta, ei fonttikoosta.
                    val inner = DialText.innerDiameter(size)
                    Text(
                        "59:59",
                        fontSize = with(density) { (inner * DialText.TIME).toSp() },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.testTag(DialText.TIME_TAG)
                    )
                }
            }
        }
        sizes.forEach { s ->
            fontScales.forEach { f ->
                size = s
                fontScale = f
                rule.waitForIdle()
                assertInside(DialText.TIME_TAG, DialText.innerDiameter(s), "taulu ${s.value} dp, fontti $f")
            }
        }
    }
}
