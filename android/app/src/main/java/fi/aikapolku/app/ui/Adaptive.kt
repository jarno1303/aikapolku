package fi.aikapolku.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ruudun kokoluokka (havainto 15.11.2026: vaakatila ja tabletit). Asettelu valitaan
 * käytettävissä olevan tilan mukaan, ei laitetyypin: sama toimii puhelimessa, tabletissa,
 * jaetussa näytössä ja taittuvissa puhelimissa.
 */
internal enum class WindowSize {
    /** Puhelin pystyssä: yksi palsta. */
    NARROW,
    /** Matala ruutu (puhelin vaakatasossa): ohut yläpalkki, kahden palstan näkymät. */
    LOW,
    /** Tabletti: sisältö keskellä enimmäisleveydellä, jotta mikään ei veny. */
    WIDE
}

internal object Adaptive {
    /** Tätä matalampi ruutu on [WindowSize.LOW]. */
    val LOW_HEIGHT = 480.dp
    /** Tätä leveämpi (ja riittävän korkea) ruutu on [WindowSize.WIDE]. */
    val WIDE_WIDTH = 600.dp
    /** Sivun sisällön enimmäisleveys tabletilla. */
    val MAX_CONTENT_WIDTH = 720.dp

    fun classify(widthDp: Int, heightDp: Int): WindowSize = when {
        heightDp < LOW_HEIGHT.value -> WindowSize.LOW
        widthDp >= WIDE_WIDTH.value -> WindowSize.WIDE
        else -> WindowSize.NARROW
    }

    /** Onko laite tabletti (kapeampikin sivu vähintään 600 dp): asennon oletus. */
    fun isTablet(smallestWidthDp: Int): Boolean = smallestWidthDp >= WIDE_WIDTH.value
}

@Composable
internal fun windowSize(): WindowSize {
    val c = LocalConfiguration.current
    return Adaptive.classify(c.screenWidthDp, c.screenHeightDp)
}

/**
 * Kahden palstan asettelu näkymän omasta tilasta: leveä ja matala alue (puhelin vaakatasossa).
 * Tabletin keskipalsta (enintään 720 dp) on riittävän korkea yhdelle palstalle.
 */
internal fun isTwoPane(width: Dp, height: Dp): Boolean = width >= 520.dp && height < 520.dp && width > height

/** Asettaa näytön asennon asiakkaan asetuksen mukaan (ohjaajana vapaa). */
@Composable
internal fun OrientationLock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity ?: return
    val caregiver = fi.aikapolku.app.caregiver.LocalCaregiver.current.isCaregiver
    val profile = fi.aikapolku.app.profiles.ProfileSession.revision
    val settings = fi.aikapolku.app.settings.SettingsRevision.value
    val mode = androidx.compose.runtime.remember(profile, settings) { fi.aikapolku.app.data.ScreenOrientation.mode(context) }
    val tablet = Adaptive.isTablet(LocalConfiguration.current.smallestScreenWidthDp)
    val requested = fi.aikapolku.app.data.ScreenOrientation.requested(mode, tablet, caregiver)
    androidx.compose.runtime.SideEffect {
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }
}
