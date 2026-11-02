package fi.aikapolku.app.caregiver

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import fi.aikapolku.app.ui.AppIcons
import android.app.Activity
import android.app.KeyguardManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.AppColors
import kotlinx.coroutines.delay

/**
 * P0-21e – Ohjaajan lukitus: käyttöliittymä.
 *
 * Näkymät kysyvät LocalCaregiver.current:lta, onko toiminto sallittu (allows) tai
 * ajavat sen run-funktiolla, joka pyytää PIN-koodin tarvittaessa.
 */
internal class CaregiverAccess(
    private val state: CaregiverAccessState,
    private val requestUnlock: (() -> Unit) -> Unit,
    val openSetup: () -> Unit,
    val offerLockOnce: () -> Unit,
    val disableLock: () -> Unit,
    val lockNow: () -> Unit,
    val forgotPin: () -> Unit,
    val deviceSecure: Boolean
) {
    val lockEnabled: Boolean get() = state.lockEnabled
    val isLocked: Boolean get() = state.isLocked

    fun allows(action: AppAction): Boolean = state.allows(action)

    /** Suorittaa toiminnon heti, tai PIN-koodin jälkeen, jos se kuuluu ohjaajalle ja lukko on päällä. */
    fun run(action: AppAction, block: () -> Unit) {
        if (allows(action)) block() else requestUnlock(block)
    }

    /** Avaa ohjaajan toiminnot (PIN-kysely), jos ne ovat lukossa. */
    fun unlock() = requestUnlock {}
}

internal val LocalCaregiver = compositionLocalOf {
    // Oletus ilman isäntää (esim. esikatselu): ei lukitusta.
    CaregiverAccess(
        state = CaregiverAccessState(lockEnabled = false, sessionOpen = false),
        requestUnlock = { it() },
        openSetup = {}, offerLockOnce = {}, disableLock = {}, lockNow = {}, forgotPin = {},
        deviceSecure = true
    )
}

@Suppress("DEPRECATION") // createConfirmDeviceCredentialIntent: ei vaadi lisäriippuvuuksia.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaregiverLockHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { CaregiverLockStore(context) }
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    val deviceSecure = keyguard?.isDeviceSecure == true

    var enabled by remember { mutableStateOf(store.isEnabled) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val openUntil = CaregiverSession.openUntil
    // Päivittää kellon sekunnin välein avauksen aikana (yläpalkin laskuri ja aikakatkaisu).
    LaunchedEffect(openUntil) {
        now = SystemClock.elapsedRealtime()
        while (openUntil > now) {
            delay(minOf(1_000L, openUntil - now + 50L))
            now = SystemClock.elapsedRealtime()
        }
    }
    val sessionOpen = openUntil > now

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askPin by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var offerOpen by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            store.disable()
            enabled = false
            CaregiverSession.close()
            askPin = false
            pending = null
            info = context.getString(R.string.ohjaajan_lukitus_on_poistettu_voit)
        }
    }

    val forgotPin: () -> Unit = {
        val intent = if (deviceSecure) keyguard?.createConfirmDeviceCredentialIntent(
            context.getString(R.string.nollaa_ohjaajan_pin_koodi),
            context.getString(R.string.vahvista_puhelimen_omalla_nayton_lukituksella)
        ) else null
        if (intent != null) {
            credentialLauncher.launch(intent)
        } else {
            info = context.getString(R.string.pin_koodin_voi_nollata_vain)
        }
    }

    val access = CaregiverAccess(
        state = CaregiverAccessState(enabled, sessionOpen),
        requestUnlock = { block ->
            if (!enabled || sessionOpen) block() else {
                pending = block
                askPin = true
            }
        },
        openSetup = { setupOpen = true },
        offerLockOnce = {
            if (!enabled && !store.lockOfferShown) {
                store.lockOfferShown = true
                offerOpen = true
            }
        },
        disableLock = {
            store.disable()
            enabled = false
            CaregiverSession.close()
        },
        lockNow = { CaregiverSession.close() },
        forgotPin = forgotPin,
        deviceSecure = deviceSecure
    )

    CompositionLocalProvider(LocalCaregiver provides access) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Näkyvä muistutus aina, kun ohjaajan toiminnot ovat auki.
            val bannerVisible = enabled && sessionOpen
            if (bannerVisible) {
                CaregiverOpenBanner(
                    remainingMs = (openUntil - now).coerceAtLeast(0L),
                    onLock = { CaregiverSession.close() }
                )
            }
            // Palkki ottaa tilarivin tilan; sisältö ei lisää sitä uudelleen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (bannerVisible) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                        else Modifier
                    )
            ) {
                content()
            }
        }

        if (askPin) {
            CaregiverPinDialog(
                store = store,
                onSuccess = {
                    askPin = false
                    CaregiverSession.open(SystemClock.elapsedRealtime())
                    val action = pending
                    pending = null
                    action?.invoke()
                },
                onDismiss = {
                    askPin = false
                    pending = null
                },
                onForgot = forgotPin
            )
        }

        if (setupOpen) {
            CaregiverPinSetupDialog(
                changing = enabled,
                deviceSecure = deviceSecure,
                onSave = { pin ->
                    val wasEnabled = enabled
                    store.enable(pin)
                    enabled = true
                    setupOpen = false
                    if (wasEnabled) {
                        // PIN vaihdettiin ohjaajana: avaus jatkuu entisellään.
                        info = context.getString(R.string.pin_koodi_on_vaihdettu)
                    } else {
                        // Käyttöönoton jälkeen lukko on heti päällä.
                        CaregiverSession.close()
                        info = context.getString(R.string.ohjaajan_lukitus_on_nyt_paalla)
                    }
                },
                onDismiss = { setupOpen = false }
            )
        }

        if (offerOpen) {
            AlertDialog(
                onDismissRequest = { offerOpen = false },
                title = { Text(stringResource(R.string.kayttaako_tata_laitetta_myos_joku)) },
                text = {
                    Text(
                        stringResource(R.string.ohjaajan_lukitus_piilottaa_ajan_muuttamisen)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        offerOpen = false
                        setupOpen = true
                    }) { Text(stringResource(R.string.ota_lukitus_kayttoon)) }
                },
                dismissButton = {
                    TextButton(onClick = { offerOpen = false }) { Text(stringResource(R.string.ei_nyt)) }
                }
            )
        }

        info?.let { message ->
            AlertDialog(
                onDismissRequest = { info = null },
                title = { Text(stringResource(R.string.ohjaajan_lukitus)) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.selva)) } }
            )
        }
    }
}

/** Yläpalkki: ohjaajan toiminnot ovat auki. Näkyy kaikissa näkymissä avauksen ajan. */
@Composable
private fun CaregiverOpenBanner(remainingMs: Long, onLock: () -> Unit) {
    val seconds = ((remainingMs + 999L) / 1000L).toInt()
    val time = "%d:%02d".format(seconds / 60, seconds % 60)
    Surface(color = Color(0xFFFDE68A), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // tilarivin alapuolelle
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.LockOpen, contentDescription = null, tint = Color(0xFF713F12), modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.ohjaaja_auki, time),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1,
                color = Color(0xFF713F12),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            TextButton(onClick = onLock, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lukitse), color = Color(0xFF713F12), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Pieni lukkopainike lukittujen ohjaajan toimintojen tilalle. */
@Composable
internal fun CaregiverLockButton(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.caregiver_label),
    /** Ahtaisiin paikkoihin (esim. Ensin–Sitten-otsikko): pelkkä lukkokuvake, ei tekstiä. */
    iconOnly: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val caregiver = LocalCaregiver.current
    TextButton(
        onClick = { caregiver.unlock() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (iconOnly) Modifier.widthIn(min = 48.dp) else Modifier)
            .semantics { contentDescription = context.getString(R.string.ohjaajan_toiminnot_lukittu_avaa_pin) },
        contentPadding = if (iconOnly) PaddingValues(4.dp) else ButtonDefaults.TextButtonContentPadding
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(20.dp))
        if (!iconOnly) {
            Text(label, color = AppColors.Teal, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
