package fi.aikapolku.app.caregiver

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton

/**
 * Laitteen tietosuoja-asetukset (laitekohtaiset): ohjaajan näkymien piilotus kuvakaappauksilta
 * ja sovellusvaihtajasta (T5, oletuksena päällä).
 */
internal object PrivacyPrefs {
    private const val PREFS = "aikapolku_privacy_v1"
    private const val KEY_SECURE = "secure_caregiver_screens"

    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun secureScreens(context: Context): Boolean = prefs(context).getBoolean(KEY_SECURE, true)

    fun setSecureScreens(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SECURE, on).apply()
        revision++
    }

    fun deviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}

/** T5: ohjaajan näkymät eivät näy kuvakaappauksissa eivätkä viimeisimmät sovellukset -näkymässä. */
@Composable
internal fun SecureWindow() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val caregiver = LocalCaregiver.current.isCaregiver
    val on = remember(PrivacyPrefs.revision) { PrivacyPrefs.secureScreens(context) }
    SideEffect {
        if (caregiver && on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * T4: paikan laitteella (koko yksikön tiedot) ohjaajan tila vaatii laitteen näytön lukituksen:
 * silloin Android salaa laitteen tiedot. Tarkistetaan uudelleen, kun sovellus palaa asetuksista.
 */
@Composable
internal fun PlaceLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val caregiver = LocalCaregiver.current.isCaregiver
    var resumed by remember { mutableIntStateOf(0) }
    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val revision = fi.aikapolku.app.link.LinkStore.revision
    val place = remember(revision, resumed) { fi.aikapolku.app.link.LinkStore(context).placeEnabled }
    val secure = remember(resumed) { PrivacyPrefs.deviceSecure(context) }
    if (caregiver && place && !secure) ScreenLockRequired() else content()
}

@Composable
private fun ScreenLockRequired() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.place_lock_title),
            modifier = Modifier.semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        Text(stringResource(R.string.place_lock_text), fontSize = 17.sp, color = AppColors.Ink, textAlign = TextAlign.Center)
        PrimaryButton(
            text = stringResource(R.string.place_lock_open_settings),
            onClick = {
                CaregiverSession.keepOpenForOwnPicker()
                runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            },
            modifier = Modifier.fillMaxWidth()
        )
        SecondaryButton(
            text = stringResource(R.string.role_to_client),
            onClick = {
                CaregiverSession.close()
                RoleSession.toClient()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Asetus: ohjaajan näkymien piilotus (lukituksen asetuksissa). */
@Composable
internal fun SecureScreensSetting() {
    val context = LocalContext.current
    var on by remember(PrivacyPrefs.revision) { mutableStateOf(PrivacyPrefs.secureScreens(context)) }
    val label = stringResource(R.string.privacy_secure_label)
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(stringResource(R.string.privacy_secure_info), fontSize = 13.sp, color = AppColors.Muted)
        }
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = { on = it; PrivacyPrefs.setSecureScreens(context, it) },
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}
