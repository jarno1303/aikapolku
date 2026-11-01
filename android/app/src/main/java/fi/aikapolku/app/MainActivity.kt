package fi.aikapolku.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.ui.theme.AikapolkuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val Teal = Color(0xFF0F766E)
private val Background = Color(0xFFF8FAFC)
private val Ink = Color(0xFF1F2937)
private val Secondary = Color(0xFF475569)
private val Coral = Color(0xFFC2413A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AikapolkuTheme {
                CaregiverLockHost {
                    AikaPolkuApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // P0-21e: ohjaajan toiminnot lukittuvat aina, kun sovellus siirtyy taustalle.
        CaregiverSession.onAppBackground()
    }
}

@Composable
private fun AikaPolkuApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardStore = remember(context) {
        P015CardStore(context.applicationContext)
    }
    val savedSelection = remember(cardStore) {
        cardStore.loadSelection()
    }
    var page by rememberSaveable { mutableStateOf("home") }
    val caregiver = LocalCaregiver.current

    var duration by rememberSaveable {
        mutableLongStateOf(300_000L)
    }
    var originalDuration by rememberSaveable {
        mutableLongStateOf(300_000L)
    }
    var remaining by rememberSaveable {
        mutableLongStateOf(P018Timer.remaining(context, "timer", 300_000L))
    }
    var running by rememberSaveable {
        mutableStateOf(P018Timer.running(context, "timer"))
    }
    var deadline by rememberSaveable {
        mutableLongStateOf(
            SystemClock.elapsedRealtime() + P018Timer.remaining(context, "timer", 300_000L)
        )
    }
    var showLastTen by rememberSaveable {
        mutableStateOf(true)
    }

    var first by rememberSaveable {
        mutableStateOf(savedSelection.firstName)
    }
    var next by rememberSaveable {
        mutableStateOf(savedSelection.nextName)
    }

    var firstPhoto by rememberSaveable {
        mutableStateOf(savedSelection.firstPhoto)
    }
    var nextPhoto by rememberSaveable {
        mutableStateOf(savedSelection.nextPhoto)
    }
    var firstDescription by rememberSaveable {
        mutableStateOf(savedSelection.firstDescription)
    }
    var nextDescription by rememberSaveable {
        mutableStateOf(savedSelection.nextDescription)
    }
    var firstVoice by rememberSaveable { mutableStateOf(savedSelection.firstVoice) }
    var nextVoice by rememberSaveable { mutableStateOf(savedSelection.nextVoice) }
    var firstVoiceAuto by rememberSaveable { mutableStateOf(savedSelection.firstVoiceAuto) }
    var nextVoiceAuto by rememberSaveable { mutableStateOf(savedSelection.nextVoiceAuto) }
    var activeTask by rememberSaveable { mutableStateOf("") }
    var voiceStartedForTimer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        first,
        next,
        firstPhoto,
        nextPhoto,
        firstDescription,
        nextDescription,
        firstVoice,
        nextVoice,
        firstVoiceAuto,
        nextVoiceAuto
    ) {
        cardStore.saveSelection(
            P015Selection(
                firstName = first,
                nextName = next,
                firstPhoto = firstPhoto,
                nextPhoto = nextPhoto,
                firstDescription = firstDescription,
                nextDescription = nextDescription,
                firstVoice = firstVoice,
                nextVoice = nextVoice,
                firstVoiceAuto = firstVoiceAuto,
                nextVoiceAuto = nextVoiceAuto
            )
        )
    }

    val ap16SoundContext =
        androidx.compose.ui.platform.LocalContext.current
    val ap16SoundScope = rememberCoroutineScope()

    LaunchedEffect(running, deadline) {
        while (running) {
            remaining = (
                deadline - SystemClock.elapsedRealtime()
            ).coerceAtLeast(0L)

            if (remaining == 0L) {
                running = false
                if (P018Timer.complete(context, "timer")) {
                    val mode = P018Timer.selectedSound(context)
                    if (mode != "off") {
                        ap16SoundScope.launch { p016PlaySound(mode) }
                    }
                }
                break
            }
            delay(100L)
        }
    }

    // P0-21e: käynnissä olevan ajastimen pysäyttäminen on lukittuna ohjaajan toiminto.
    val guardTimerStop: (() -> Unit) -> Unit = { action ->
        if (running && caregiver.isLocked) caregiver.run(AppAction.PAUSE_TIMER, action) else action()
    }

    val goBack: () -> Unit = {
        if (page == "timer" && activeTask.isNotBlank()) {
            guardTimerStop {
                if (running) {
                    remaining = (
                        deadline - SystemClock.elapsedRealtime()
                    ).coerceAtLeast(0L)
                    running = false
                }
                P018Timer.stop(context, "timer", remaining)
                page = "firstthen"
            }
        } else if (page == "manual") {
            page = "firstthen"
        } else {
            page = "home"
        }
    }

    BackHandler(enabled = page != "home") {
        goBack()
    }

    val rootScroll = rememberScrollState()
    LaunchedEffect(page) { rootScroll.scrollTo(0) }

    Scaffold(
        containerColor = Background,
        topBar = {
            if (page != "home") {
                Surface(color = Background) {
                    TextButton(
                        onClick = goBack,
                        modifier = Modifier.padding(
                            start = 12.dp,
                            top = 6.dp
                        )
                    ) {
                        Text("‹ Takaisin", color = Teal)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // The programmes screen owns its scrolling and keeps action buttons visible.
                .then(if (page == "programs") Modifier else Modifier.verticalScroll(rootScroll))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            when (page) {
                "home" -> HomeScreen { destination ->
                    page = destination
                    if (destination == "timer") activeTask = ""
                }

                "timer" -> P013TimerScreen(
                    remaining = remaining,
                    duration = duration,
                    running = running,
                    showLastTen = showLastTen,
                    activeTask = activeTask,
                    firstPhoto = firstPhoto,
                    nextPhoto = nextPhoto,
                    nextActivity = next,
                    onGoNext = {
                        running = false
                        P018Timer.stop(context, "timer", 0L)
                        activeTask = ""

                        if (next != "Valitse toiminto") {
                            first = next
                            firstPhoto = nextPhoto
                            firstDescription = nextDescription
                            firstVoice = nextVoice
                            firstVoiceAuto = nextVoiceAuto
                            page = "last"
                            if (firstVoiceAuto) P017VoicePlayer.play(context, firstVoice)
                        } else {
                            page = "done"
                        }
                    },
                    onShowLastTen = { showLastTen = it },
                    onPreset = { minutes ->
                        running = false
                        P018Timer.stop(context, "timer", minutes * 60_000L)
                        voiceStartedForTimer = false
                        duration = minutes * 60_000L
                        originalDuration = duration
                        remaining = duration
                    },
                    onStartPause = {
                        if (running) {
                            remaining = P018Timer.remaining(context, "timer", remaining)
                            P018Timer.stop(context, "timer", remaining)
                            running = false
                        } else if (!P018Timer.allowed(context)) {
                            P018Timer.requestPermissions(context)
                        } else {
                            if (remaining == 0L) {
                                duration = originalDuration
                                remaining = originalDuration
                            }
                            deadline = P018Timer.start(
                                context, "timer", remaining,
                                if (activeTask.isBlank()) "Ajastin" else activeTask
                            )
                            running = true
                            if (remaining == 0L) voiceStartedForTimer = false
                            if (activeTask.isNotBlank() && !voiceStartedForTimer) {
                                voiceStartedForTimer = true
                                if (firstVoiceAuto) P017VoicePlayer.play(context, firstVoice)
                            }
                        }
                    },
                    onReset = {
                        running = false
                        P018Timer.stop(context, "timer", originalDuration)
                        voiceStartedForTimer = false
                        duration = originalDuration
                        remaining = originalDuration
                    }
                )

                "firstthen" -> EnhancedFirstThenScreen(
                    first = first,
                    next = next,
                    firstPhoto = firstPhoto,
                    nextPhoto = nextPhoto,
                    firstDescription = firstDescription,
                    nextDescription = nextDescription,
                    firstVoice = firstVoice,
                    nextVoice = nextVoice,
                    firstVoiceAuto = firstVoiceAuto,
                    nextVoiceAuto = nextVoiceAuto,
                    onFirst = { first = it },
                    onNext = { next = it },
                    onFirstPhoto = { firstPhoto = it },
                    onNextPhoto = { nextPhoto = it },
                    onFirstDescription = { firstDescription = it },
                    onNextDescription = { nextDescription = it },
                    onFirstVoice = { path, auto ->
                        firstVoice = path
                        firstVoiceAuto = auto
                    },
                    onNextVoice = { path, auto ->
                        nextVoice = path
                        nextVoiceAuto = auto
                    },
                    onDone = {
                        guardTimerStop {
                            running = false
                            P018Timer.stop(context, "timer", remaining)
                            activeTask = ""
                            page = "manual"
                            if (firstVoiceAuto) P017VoicePlayer.play(context, firstVoice)
                        }
                    },
                    onStartTimer = { minutes ->
                        guardTimerStop {
                            running = false
                            P018Timer.stop(context, "timer", minutes * 60_000L)
                            duration = minutes * 60_000L
                            originalDuration = duration
                            remaining = duration
                            activeTask = first
                            deadline = 0L
                            running = false
                            page = "timer"
                            voiceStartedForTimer = false
                        }
                    }
                )

                "programs" -> P016ProgramsScreen()

                "caregiver" -> CaregiverSettingsScreen()

                "manual" -> P014ManualScreen(
                    first = first,
                    firstPhoto = firstPhoto,
                    next = next,
                    nextPhoto = nextPhoto,
                    onNext = {
                        running = false
                        if (next != "Valitse toiminto") {
                            first = next
                            firstPhoto = nextPhoto
                            firstDescription = nextDescription
                            firstVoice = nextVoice
                            firstVoiceAuto = nextVoiceAuto
                            page = "last"
                            if (firstVoiceAuto) P017VoicePlayer.play(context, firstVoice)
                        }
                    }
                )

                "last" -> P011LastActivityScreen(
                    name = first,
                    imagePath = firstPhoto,
                    onFinish = {
                        running = false
                        page = "done"
                    },
                    onAddNext = if (caregiver.allows(AppAction.EDIT_CARDS)) {
                        {
                            next = "Valitse toiminto"
                            nextPhoto = ""
                            nextDescription = ""
                            nextVoice = ""
                            nextVoiceAuto = false
                            page = "firstthen"
                        }
                    } else null
                )

                "done" -> P011DoneScreen(
                    onHome = {
                        activeTask = ""
                        page = "home"
                    }
                )
            }

            if (page != "home" &&
                page != "firstthen" &&
                page != "programs" &&
                page != "last" &&
                page != "done" &&
                !(page == "timer" && activeTask.isNotBlank())) {
                TextButton(
                    onClick = goBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
    if (page == "timer" && activeTask.isNotBlank())
        "Takaisin Ensin – Sitten"
    else "Takaisin aloitukseen",
    fontSize = 16.sp
)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(open: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ap_home_brand),
            contentDescription = null,
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(17.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("AikaPolku", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Selkeyttä arkeen, askel kerrallaan", fontSize = 14.sp, color = Secondary)
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Mitä haluat tehdä?", fontSize = 22.sp,
        fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(6.dp))

    HomeCard(
        symbol = "timer",
        title = "Ajastin",
        subtitle = "Aseta aika ja keskity",
        onClick = { open("timer") }
    )
    HomeCard(
        symbol = "flow",
        title = "Ensin – Sitten",
        subtitle = "Suunnittele kaksi askelta",
        onClick = { open("firstthen") }
    )
    HomeCard(
        symbol = "programs",
        title = "Päiväohjelmat",
        subtitle = "Näe päivän kulku",
        onClick = { open("programs") }
    )

    val caregiver = LocalCaregiver.current
    TextButton(
        onClick = { caregiver.run(AppAction.SETTINGS) { open("caregiver") } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            if (caregiver.isLocked) "🔒 Ohjaajan asetukset" else "⚙ Ohjaajan asetukset",
            color = Teal,
            fontSize = 15.sp
        )
    }

    Spacer(Modifier.height(14.dp))
    Surface(
        color = Color(0xFFE6F4F1),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(
            "Näe, mitä teet nyt ja mitä seuraavaksi.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp),
            fontSize = 14.sp,
            color = Color(0xFF145B55)
        )
    }
}

@Composable
private fun HomeCard(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val icon = when (symbol) {
        "flow" -> R.drawable.ap_home_flow
        "programs" -> R.drawable.ap_home_programs
        else -> R.drawable.ap_home_timer
    }
    val surfaceColor = when (symbol) {
        "flow" -> Color(0xFFF1ECFB)
        "programs" -> Color(0xFFE8F3FA)
        else -> Color(0xFFE4F4EF)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(subtitle, fontSize = 14.sp, color = Secondary)
            }
            Text("›", fontSize = 28.sp, color = Teal)
        }
    }
}

// AIKAPOLKU_P0_07
@Composable
private fun TimerScreen(
    remaining: Long,
    duration: Long,
    running: Boolean,
    showLastTen: Boolean,
    activeTask: String,
    firstPhoto: String,
    nextPhoto: String,
    nextActivity: String,
    onGoNext: () -> Unit,
    onShowLastTen: (Boolean) -> Unit,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
    onSubtractMinute: () -> Unit
) {
    val guided = activeTask.isNotBlank()
    val finished = guided && remaining == 0L

    var moreOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var settingsOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var confirmReset by rememberSaveable {
        mutableStateOf(false)
    }

    if (guided) {
        AikaFlowHeader()
    } else {
        Text(
            "Ajastin",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
    }

    if (finished) {
        PolishedCompletedCards(
            first = activeTask,
            next = nextActivity,
            nextPhoto = nextPhoto
        )
    } else if (guided) {
        PolishedActiveCards(
            first = activeTask,
            firstPhoto = firstPhoto,
            next = nextActivity,
            nextPhoto = nextPhoto
        )
    } else {
        Text(
            "Valitse aika ja paina Aloita.",
            color = Secondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 5, 10, 15, 30, 60).forEach { min ->
                OutlinedButton(
                    onClick = { onPreset(min) }
                ) {
                    Text("$min min")
                }
            }
        }
    }

    val seconds = ((remaining + 999L) / 1000L).toInt()
    val time = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        seconds / 60,
        seconds % 60
    )
    val progress = (
        remaining.toFloat() /
        duration.coerceAtLeast(1L).toFloat()
    ).coerceIn(0f, 1f)

    if (!finished) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(
                        if (LocalConfiguration.current.screenHeightDp < 740)
                            192.dp
                        else if (guided) 228.dp
                        else 218.dp
                    )
                    .shadow(5.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (running)
                            "Keskeytä ajastin"
                        else "Käynnistä tai jatka ajastinta",
                        onClick = onStartPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                AikaTimerRing(
                    progress = progress,
                    urgent = running && seconds <= 10,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.offset(
                        y = if (guided) (-21).dp else 0.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val finalCountdown =
                        showLastTen && running && seconds in 1..10

                    Text(
                        if (finalCountdown) seconds.toString()
                        else time,
                        fontSize = if (finalCountdown) 58.sp else 39.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (finalCountdown) Coral else Ink,
                        maxLines = 1
                    )
                    Text(
                        if (seconds == 0) "Aika päättyi"
                        else "aikaa jäljellä",
                        color = Secondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (running) "Ⅱ Tauko"
                        else if (remaining < duration && remaining > 0L)
                            "▶ Jatka"
                        else "▶ Aloita",
                        color = Teal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (guided) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 11.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { moreOpen = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = "Ajan säätö"
                                    }
                            ) {
                                Text(
                                    "⋯",
                                    fontSize = 27.sp,
                                    color = Teal
                                )
                            }

                            DropdownMenu(
                                expanded = moreOpen,
                                onDismissRequest = {
                                    moreOpen = false
                                }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("−1 minuutti") },
                                    enabled = remaining > 0L,
                                    onClick = {
                                        moreOpen = false
                                        onSubtractMinute()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+1 minuutti") },
                                    onClick = {
                                        moreOpen = false
                                        onAddMinute()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nollaa ajastin") },
                                    onClick = {
                                        moreOpen = false
                                        confirmReset = true
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = { settingsOpen = true },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription =
                                        "Ajastimen asetukset"
                                }
                        ) {
                            Text(
                                "⚙",
                                fontSize = 23.sp,
                                color = Teal
                            )
                        }
                    }
                }
            }
        }
    } else {
        Button(
            onClick = onGoNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Teal
            )
        ) {
            Text(
                if (nextActivity == "Valitse toiminto")
                    "Valmis – takaisin"
                else "Siirry seuraavaan",
                fontSize = 18.sp
            )
        }
    }

    if (showLastTen && running && seconds in 1..10 && !guided) {
        Text(
            "Viimeiset $seconds sekuntia",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Coral,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    if (!guided) {
        Button(
            onClick = onStartPause,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Teal
            )
        ) {
            Text(
                if (running) "Tauko"
                else if (remaining in 1 until duration) "Jatka"
                else "Aloita",
                fontSize = 19.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSubtractMinute,
                enabled = remaining > 0L,
                modifier = Modifier.weight(1f)
            ) {
                Text("−1 min")
            }
            OutlinedButton(
                onClick = onAddMinute,
                modifier = Modifier.weight(1f)
            ) {
                Text("+1 min")
            }
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Nollaa")
        }

        TextButton(
            onClick = { settingsOpen = !settingsOpen }
        ) {
            Text(
                if (settingsOpen) "Piilota asetukset"
                else "Ajastimen asetukset"
            )
        }

        if (settingsOpen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Viimeisten 10 sekunnin laskenta",
                    modifier = Modifier.weight(1f),
                    color = Ink
                )
                Switch(
                    checked = showLastTen,
                    onCheckedChange = onShowLastTen
                )
            }
        }
    }

    if (guided && settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Ajastimen asetukset") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Viimeisten 10 sekunnin laskenta",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showLastTen,
                        onCheckedChange = onShowLastTen
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsOpen = false }
                ) {
                    Text("Valmis")
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Nollataanko ajastin?") },
            text = {
                Text("Ajastin palautetaan alkuperäiseen aikaan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    }
                ) {
                    Text("Nollaa")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReset = false }
                ) {
                    Text("Peruuta")
                }
            }
        )
    }
}
private val activities = listOf(
    "🎨" to "Askartelu",
    "🍽" to "Ruokailu",
    "🚶" to "Ulkoilu",
    "🎮" to "Pelaaminen",
    "📖" to "Lukeminen",
    "🎵" to "Musiikki",
    "🧩" to "Tehtävät",
    "☕" to "Tauko",
    "🛏" to "Lepo",
    "🪥" to "Hampaiden pesu"
)

@Composable
private fun FirstThenScreen(
    first: String,
    next: String,
    onFirst: (String) -> Unit,
    onNext: (String) -> Unit,
    onDone: () -> Unit
) {
    var picking by remember { mutableStateOf<String?>(null) }

    Text(
        "Ensin – Sitten",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Ink
    )
    Text(
        "Valitse kaksi toimintoa.",
        color = Secondary
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityCard(
            label = "ENSIN",
            name = first,
            modifier = Modifier.weight(1f),
            onClick = { picking = "first" }
        )
        ActivityCard(
            label = "SITTEN",
            name = next,
            modifier = Modifier.weight(1f),
            onClick = { picking = "next" }
        )
    }

    Text(
        "Vaihda toimintoa koskettamalla korttia.",
        color = Secondary,
        fontSize = 14.sp
    )

    Button(
        onClick = onDone,
        enabled = next != "Valitse toiminto",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Teal
        )
    ) {
        Text("Valmis – siirry seuraavaan")
    }

    if (picking != null) {
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                Text("Valitse toiminto")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    activities.forEach { (symbol, name) ->
                        TextButton(
                            onClick = {
                                if (picking == "first") {
                                    onFirst(name)
                                } else {
                                    onNext(name)
                                }
                                picking = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$symbol  $name",
                                fontSize = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) {
                    Text("Sulje")
                }
            }
        )
    }
}

@Composable
private fun ActivityCard(
    label: String,
    name: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val symbol = activities
        .firstOrNull { it.second == name }
        ?.first ?: "➕"

    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 225.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                label,
                color = Teal,
                fontWeight = FontWeight.Bold
            )
            Text(symbol, fontSize = 52.sp)
            Text(
                name,
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Vaihda",
                color = Teal,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ProgramsScreen() {
    Text(
        "Päiväohjelmat",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Ink
    )

    Text(
        "Esimerkki kuvallisesta päiväohjelmasta",
        color = Secondary
    )

    val example = listOf(
        Triple("🌅", "Aamutoimet", "8.00"),
        Triple("🍽", "Aamupala", "8.30"),
        Triple("🚶", "Ulkoilu", "20 min"),
        Triple("🎨", "Askartelu", "30 min"),
        Triple("☕", "Tauko", "Edellisen jälkeen")
    )

    example.forEach { (symbol, name, time) ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(symbol, fontSize = 30.sp)
                Column {
                    Text(
                        name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(time, color = Secondary)
                }
            }
        }
    }

    Text(
        "Tämä on esimerkkiohjelma. Omien rutiinien " +
        "rakentaminen ja tallennus toteutetaan seuraavaksi.",
        color = Secondary,
        fontSize = 14.sp
    )
}