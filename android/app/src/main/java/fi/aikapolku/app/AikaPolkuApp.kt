package fi.aikapolku.app

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.CaregiverSettingsScreen
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.firstthen.FirstThenScreen
import fi.aikapolku.app.firstthen.FlowDoneScreen
import fi.aikapolku.app.firstthen.LastActivityScreen
import fi.aikapolku.app.firstthen.ManualFlowScreen
import fi.aikapolku.app.programs.ProgramsScreen
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.timer.TimerScreen
import fi.aikapolku.app.timer.playCompletionSound
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.voice.VoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sovelluksen näkymät. */
internal enum class AppPage { HOME, TIMER, FIRST_THEN, PROGRAMS, CAREGIVER, MANUAL, LAST, DONE }

/** Sovelluksen navigointi ja Ensin–Sitten-kulun yhteinen tila. */
@Composable
internal fun AikaPolkuApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardStore = remember(context) {
        CardStore(context.applicationContext)
    }
    val savedSelection = remember(cardStore) {
        cardStore.loadSelection()
    }
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
    val caregiver = LocalCaregiver.current

    var duration by rememberSaveable {
        mutableLongStateOf(300_000L)
    }
    var originalDuration by rememberSaveable {
        mutableLongStateOf(300_000L)
    }
    var remaining by rememberSaveable {
        mutableLongStateOf(BackgroundTimer.remaining(context, "timer", 300_000L))
    }
    var running by rememberSaveable {
        mutableStateOf(BackgroundTimer.running(context, "timer"))
    }
    var deadline by rememberSaveable {
        mutableLongStateOf(
            SystemClock.elapsedRealtime() + BackgroundTimer.remaining(context, "timer", 300_000L)
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
            FirstThenSelection(
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
                if (BackgroundTimer.complete(context, "timer")) {
                    val mode = BackgroundTimer.selectedSound(context)
                    if (mode != "off") {
                        ap16SoundScope.launch { playCompletionSound(mode) }
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
        if (page == AppPage.TIMER && activeTask.isNotBlank()) {
            guardTimerStop {
                if (running) {
                    remaining = (
                        deadline - SystemClock.elapsedRealtime()
                    ).coerceAtLeast(0L)
                    running = false
                }
                BackgroundTimer.stop(context, "timer", remaining)
                page = AppPage.FIRST_THEN
            }
        } else if (page == AppPage.MANUAL) {
            page = AppPage.FIRST_THEN
        } else {
            page = AppPage.HOME
        }
    }

    BackHandler(enabled = page != AppPage.HOME) {
        goBack()
    }

    val rootScroll = rememberScrollState()
    LaunchedEffect(page) { rootScroll.scrollTo(0) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            if (page != AppPage.HOME) {
                // Tilarivin alapuolella; teksti tasattu sisällön ja otsikoiden reunaan (12 dp).
                Surface(color = AppColors.Background) {
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
                            Text(stringResource(R.string.takaisin), color = AppColors.Teal, fontSize = 17.sp)
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
                .then(if (page == AppPage.PROGRAMS) Modifier else Modifier.verticalScroll(rootScroll))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            when (page) {
                AppPage.HOME -> HomeScreen { destination ->
                    page = destination
                    if (destination == AppPage.TIMER) activeTask = ""
                }

                AppPage.TIMER -> TimerScreen(
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
                        BackgroundTimer.stop(context, "timer", 0L)
                        activeTask = ""

                        if (next != context.getString(R.string.valitse_toiminto)) {
                            first = next
                            firstPhoto = nextPhoto
                            firstDescription = nextDescription
                            firstVoice = nextVoice
                            firstVoiceAuto = nextVoiceAuto
                            page = AppPage.LAST
                            if (firstVoiceAuto) VoicePlayer.play(context, firstVoice)
                        } else {
                            page = AppPage.DONE
                        }
                    },
                    onShowLastTen = { showLastTen = it },
                    onPreset = { minutes ->
                        running = false
                        BackgroundTimer.stop(context, "timer", minutes * 60_000L)
                        voiceStartedForTimer = false
                        duration = minutes * 60_000L
                        originalDuration = duration
                        remaining = duration
                    },
                    onStartPause = {
                        if (running) {
                            remaining = BackgroundTimer.remaining(context, "timer", remaining)
                            BackgroundTimer.stop(context, "timer", remaining)
                            running = false
                        } else if (!BackgroundTimer.allowed(context)) {
                            BackgroundTimer.requestPermissions(context)
                        } else {
                            if (remaining == 0L) {
                                duration = originalDuration
                                remaining = originalDuration
                            }
                            deadline = BackgroundTimer.start(
                                context, "timer", remaining,
                                if (activeTask.isBlank()) context.getString(R.string.ajastin) else activeTask
                            )
                            running = true
                            if (remaining == 0L) voiceStartedForTimer = false
                            if (activeTask.isNotBlank() && !voiceStartedForTimer) {
                                voiceStartedForTimer = true
                                if (firstVoiceAuto) VoicePlayer.play(context, firstVoice)
                            }
                        }
                    },
                    onReset = {
                        running = false
                        BackgroundTimer.stop(context, "timer", originalDuration)
                        voiceStartedForTimer = false
                        duration = originalDuration
                        remaining = originalDuration
                    }
                )

                AppPage.FIRST_THEN -> FirstThenScreen(
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
                            BackgroundTimer.stop(context, "timer", remaining)
                            activeTask = ""
                            page = AppPage.MANUAL
                            if (firstVoiceAuto) VoicePlayer.play(context, firstVoice)
                        }
                    },
                    onStartTimer = { minutes ->
                        guardTimerStop {
                            running = false
                            BackgroundTimer.stop(context, "timer", minutes * 60_000L)
                            duration = minutes * 60_000L
                            originalDuration = duration
                            remaining = duration
                            activeTask = first
                            deadline = 0L
                            running = false
                            page = AppPage.TIMER
                            voiceStartedForTimer = false
                        }
                    }
                )

                AppPage.PROGRAMS -> ProgramsScreen()

                AppPage.CAREGIVER -> CaregiverSettingsScreen()

                AppPage.MANUAL -> ManualFlowScreen(
                    first = first,
                    firstPhoto = firstPhoto,
                    next = next,
                    nextPhoto = nextPhoto,
                    onNext = {
                        running = false
                        if (next != context.getString(R.string.valitse_toiminto)) {
                            first = next
                            firstPhoto = nextPhoto
                            firstDescription = nextDescription
                            firstVoice = nextVoice
                            firstVoiceAuto = nextVoiceAuto
                            page = AppPage.LAST
                            if (firstVoiceAuto) VoicePlayer.play(context, firstVoice)
                        }
                    }
                )

                AppPage.LAST -> LastActivityScreen(
                    name = first,
                    imagePath = firstPhoto,
                    onFinish = {
                        running = false
                        page = AppPage.DONE
                    },
                    onAddNext = if (caregiver.allows(AppAction.EDIT_CARDS)) {
                        {
                            next = context.getString(R.string.valitse_toiminto)
                            nextPhoto = ""
                            nextDescription = ""
                            nextVoice = ""
                            nextVoiceAuto = false
                            page = AppPage.FIRST_THEN
                        }
                    } else null
                )

                AppPage.DONE -> FlowDoneScreen(
                    onHome = {
                        activeTask = ""
                        page = AppPage.HOME
                    }
                )
            }

            if (page != AppPage.HOME &&
                page != AppPage.FIRST_THEN &&
                page != AppPage.PROGRAMS &&
                page != AppPage.LAST &&
                page != AppPage.DONE &&
                !(page == AppPage.TIMER && activeTask.isNotBlank())) {
                TextButton(
                    onClick = goBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
    if (page == AppPage.TIMER && activeTask.isNotBlank())
        stringResource(R.string.takaisin_ensin_sitten)
    else stringResource(R.string.takaisin_aloitukseen),
    fontSize = 16.sp
)
                }
            }
        }
    }
}
