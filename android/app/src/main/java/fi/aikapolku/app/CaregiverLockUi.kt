package fi.aikapolku.app

import android.app.Activity
import android.app.KeyguardManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val LockTeal = Color(0xFF0F766E)

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
            info = "Ohjaajan lukitus on poistettu. Voit asettaa uuden PIN-koodin Ohjaajan asetuksista."
        }
    }

    val forgotPin: () -> Unit = {
        val intent = if (deviceSecure) keyguard?.createConfirmDeviceCredentialIntent(
            "Nollaa ohjaajan PIN-koodi",
            "Vahvista puhelimen omalla näytön lukituksella."
        ) else null
        if (intent != null) {
            credentialLauncher.launch(intent)
        } else {
            info = "PIN-koodin voi nollata vain puhelimen omalla näytön lukituksella " +
                "(PIN, kuvio tai sormenjälki), eikä puhelimessa ole sitä käytössä. " +
                "Ota näytön lukitus käyttöön puhelimen asetuksista ja yritä uudelleen."
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
                        info = "PIN-koodi on vaihdettu."
                    } else {
                        // Käyttöönoton jälkeen lukko on heti päällä.
                        CaregiverSession.close()
                        info = "Ohjaajan lukitus on nyt päällä. Ohjaajan toiminnot avataan " +
                            "🔒-painikkeella ja PIN-koodilla. Ne ovat auki 2 minuuttia kerrallaan."
                    }
                },
                onDismiss = { setupOpen = false }
            )
        }

        if (offerOpen) {
            AlertDialog(
                onDismissRequest = { offerOpen = false },
                title = { Text("Käyttääkö tätä laitetta myös joku muu?") },
                text = {
                    Text(
                        "Ohjaajan lukitus piilottaa ajan muuttamisen, ohjelmien muokkauksen ja " +
                            "asetukset PIN-koodin taakse. Tarvitsen apua, Tarvitsen tauon ja " +
                            "Valmis toimivat aina.\n\nLukituksen voi ottaa käyttöön myöhemmin " +
                            "myös Ohjaajan asetuksista."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        offerOpen = false
                        setupOpen = true
                    }) { Text("Ota lukitus käyttöön") }
                },
                dismissButton = {
                    TextButton(onClick = { offerOpen = false }) { Text("Ei nyt") }
                }
            )
        }

        info?.let { message ->
            AlertDialog(
                onDismissRequest = { info = null },
                title = { Text("Ohjaajan lukitus") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { info = null }) { Text("Selvä") } }
            )
        }
    }
}

@Composable
private fun CaregiverPinDialog(
    store: CaregiverLockStore,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onForgot: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(store.lockoutRemainingMs()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(lockoutMs > 0L) {
        while (store.lockoutRemainingMs() > 0L) {
            lockoutMs = store.lockoutRemainingMs()
            delay(1_000L)
        }
        lockoutMs = 0L
    }
    LaunchedEffect(lockoutMs == 0L) {
        if (lockoutMs == 0L) {
            delay(150L) // dialogin ikkunan on oltava liitetty ennen fokusointia
            runCatching { focus.requestFocus() }
        }
    }

    fun submit(value: String) {
        when (val result = store.verify(value)) {
            PinResult.Ok -> onSuccess()
            is PinResult.Wrong -> {
                pin = ""
                message = "Väärä PIN-koodi. Yrityksiä jäljellä: ${result.attemptsLeft}."
            }
            is PinResult.LockedOut -> {
                pin = ""
                message = null
                lockoutMs = result.remainingMs
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔒 Ohjaajan toiminnot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Anna ohjaajan PIN-koodi.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { typed ->
                        val digits = typed.filter { it.isDigit() }.take(CaregiverLockStore.PIN_LENGTH)
                        pin = digits
                        if (digits.length == CaregiverLockStore.PIN_LENGTH) submit(digits)
                    },
                    enabled = lockoutMs == 0L,
                    label = { Text("PIN-koodi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                if (lockoutMs > 0L) {
                    val seconds = (lockoutMs + 999L) / 1000L
                    Text(
                        "Liian monta väärää yritystä. Yritä uudelleen ${seconds} sekunnin kuluttua.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onForgot) { Text("Unohditko PIN-koodin?") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Peruuta") }
        }
    )
}

@Composable
private fun CaregiverPinSetupDialog(
    changing: Boolean,
    deviceSecure: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val length = CaregiverLockStore.PIN_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (changing) "Vaihda PIN-koodi" else "Ota ohjaajan lukitus käyttöön") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Valitse nelinumeroinen PIN-koodi, jota asiakas ei tiedä.")
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text("Uusi PIN-koodi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text("PIN-koodi uudelleen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!deviceSecure) {
                    Text(
                        "Huom: puhelimessa ei ole näytön lukitusta. Unohtunutta PIN-koodia ei voi " +
                            "nollata, ennen kuin puhelimen näytön lukitus otetaan käyttöön.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = first.length == length && second.length == length,
                onClick = {
                    val problem = CaregiverLockStore.pinProblem(first)
                        ?: if (first != second) "PIN-koodit eivät täsmää." else null
                    if (problem != null) {
                        error = problem
                        second = ""
                    } else {
                        onSave(first)
                    }
                }
            ) { Text("Tallenna") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Peruuta") }
        }
    )
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
            Text(
                "🔓 Ohjaaja auki · $time",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = Color(0xFF713F12),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            TextButton(onClick = onLock, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Lukitse", color = Color(0xFF713F12), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Pieni lukkopainike lukittujen ohjaajan toimintojen tilalle. */
@Composable
internal fun CaregiverLockButton(
    modifier: Modifier = Modifier,
    label: String = "Ohjaaja",
    /** Ahtaisiin paikkoihin (esim. Ensin–Sitten-otsikko): pelkkä 🔒, ei tekstiä. */
    iconOnly: Boolean = false
) {
    val caregiver = LocalCaregiver.current
    TextButton(
        onClick = { caregiver.unlock() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (iconOnly) Modifier.widthIn(min = 48.dp) else Modifier)
            .semantics { contentDescription = "Ohjaajan toiminnot, lukittu. Avaa PIN-koodilla." },
        contentPadding = if (iconOnly) PaddingValues(4.dp) else ButtonDefaults.TextButtonContentPadding
    ) {
        Text(if (iconOnly) "🔒" else "🔒 $label", color = LockTeal, fontSize = if (iconOnly) 20.sp else 14.sp)
    }
}

/** Ohjaajan asetukset -sivu. Sivulle pääsee vain ohjaajana (ks. MainActivity). */
@Composable
internal fun CaregiverSettingsScreen() {
    val caregiver = LocalCaregiver.current
    var confirmDisable by remember { mutableStateOf(false) }

    Text("Ohjaajan asetukset", fontSize = 28.sp, fontWeight = FontWeight.Bold)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4F1))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Ohjaajan lukitus", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (caregiver.lockEnabled) "Tila: käytössä" else "Tila: ei käytössä",
                fontWeight = FontWeight.SemiBold,
                color = LockTeal
            )
            Text(
                "Lukitus piilottaa PIN-koodin taakse ajan muuttamisen, ajastimen tauon ja nollauksen, " +
                    "lisäajan, päiväohjelman lopettamisen, ohjelmien ja korttien muokkauksen sekä asetukset."
            )
            Text(
                "Aina käytettävissä: Tarvitsen apua, Tarvitsen tauon, Valmis, osavaiheen ohitus " +
                    "sekä ajastimen Aloita ja Jatka."
            )
            Text(
                "PIN-koodilla avatut ohjaajan toiminnot ovat auki 2 minuuttia, minkä ajan " +
                    "yläreunassa näkyy muistutus. Lukitus menee päälle myös, kun sovellus " +
                    "suljetaan tai siirtyy taustalle.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!caregiver.lockEnabled) {
                Button(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Ota lukitus käyttöön", fontSize = 17.sp) }
            } else {
                Button(
                    onClick = { caregiver.lockNow() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("🔒 Lukitse nyt", fontSize = 17.sp) }
                OutlinedButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Vaihda PIN-koodi") }
                TextButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { confirmDisable = true } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Poista lukitus") }
            }

            if (!caregiver.deviceSecure) {
                Text(
                    "Puhelimessa ei ole näytön lukitusta. Unohtunut PIN-koodi nollataan puhelimen " +
                        "omalla lukituksella, joten sen käyttöönotto on suositeltavaa.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Vinkki: näytön kiinnitys", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Androidin näytön kiinnitys pitää AikaPolun näkyvissä niin, ettei sovelluksesta " +
                    "voi siirtyä muihin sovelluksiin. Ota se käyttöön puhelimen asetuksista hakemalla " +
                    "\"Näytön kiinnitys\" tai \"Sovelluksen kiinnitys\"."
            )
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Poistetaanko ohjaajan lukitus?") },
            text = { Text("Kaikki toiminnot ovat tämän jälkeen kenen tahansa käytettävissä.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    caregiver.disableLock()
                }) { Text("Poista lukitus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Peruuta") }
            }
        )
    }
}
