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
                // Tilarivin alapuolella; teksti tasattu sisällön ja otsikoiden reunaan (12 dp).
                Surface(color = Background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 4.dp)
                            .heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = goBack,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("‹ Takaisin", color = Teal, fontSize = 17.sp)
                        }
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

