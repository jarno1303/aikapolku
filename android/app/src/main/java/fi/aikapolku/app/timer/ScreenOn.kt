package fi.aikapolku.app.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Näyttö pysyy päällä, kun jokin aika on käynnissä (päiväohjelman tehtävä, Ajastin,
 * Ensin–Sitten), jottei ajan kulku katoa asiakkaalta kesken tehtävän. Syyt lasketaan
 * yhteen: näyttö vapautuu vasta, kun mikään aika ei ole käynnissä. Muulloin puhelin
 * sammuttaa näytön tavalliseen tapaan.
 */
internal object ScreenOn {
    private val reasons = mutableSetOf<String>()

    fun active(reason: String): Boolean = reason in reasons
    fun any(): Boolean = reasons.isNotEmpty()

    fun update(activity: Activity?, reason: String, on: Boolean) {
        if (on) reasons += reason else reasons -= reason
        // View-kohtainen lippu (ei ikkunalippu): vaikuttaa vain, kun sovellus on näkyvissä.
        activity?.window?.decorView?.keepScreenOn = reasons.isNotEmpty()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Pitää näytön päällä niin kauan kuin [active] on tosi (ks. [ScreenOn]). */
@Composable
internal fun KeepScreenOnWhile(reason: String, active: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, active) {
        ScreenOn.update(activity, reason, active)
        onDispose { ScreenOn.update(activity, reason, false) }
    }
}
