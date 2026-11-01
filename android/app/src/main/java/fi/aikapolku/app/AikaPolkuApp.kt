package fi.aikapolku.app

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
import fi.aikapolku.app.firstthen.FirstThenCards
import fi.aikapolku.app.firstthen.FirstThenScreen
import fi.aikapolku.app.firstthen.FlowDoneScreen
import fi.aikapolku.app.firstthen.FlowTimer
import fi.aikapolku.app.firstthen.LastActivityScreen
import fi.aikapolku.app.firstthen.ManualFlowScreen
import fi.aikapolku.app.programs.ProgramsScreen
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.timer.BackgroundTimerPort
import fi.aikapolku.app.timer.Tick
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
    val cardStore = remember(context) { CardStore(context.applicationContext) }
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
    val caregiver = LocalCaregiver.current

    val timerPort = remember(context) { BackgroundTimerPort(context.applicationContext, "timer") }
    val timer = rememberSaveable(saver = FlowTimer.saver(timerPort)) { FlowTimer(timerPort) }
    val cards = rememberSaveable(saver = FirstThenCards.saver) { FirstThenCards(cardStore.loadSelection()) }
    val sel = cards.selection
    var showLastTen by rememberSaveable { mutableStateOf(true) }

    // Valinnat tallennetaan aina niiden muuttuessa.
    LaunchedEffect(sel) { cardStore.saveSelection(sel) }

    // Näkymät näyttävät valitsemattoman kortin tekstinä "Valitse toiminto".
    val placeholder = stringResource(R.string.valitse_toiminto)
    val firstShown = sel.firstName.ifBlank { placeholder }
    val nextShown = sel.nextName.ifBlank { placeholder }
    fun chosen(name: String) = if (name == placeholder) "" else name

    val soundScope = rememberCoroutineScope()
    LaunchedEffect(timer.running, timer.deadline) {
        while (timer.running) {
            when (timer.tick()) {
                Tick.RUNNING -> delay(100L)
                Tick.ENDED -> {
                    val mode = BackgroundTimer.selectedSound(context)
                    if (mode != "off") soundScope.launch { playCompletionSound(mode) }
                    break
                }
                Tick.ENDED_SILENT -> break
            }
        }
    }

    fun playFirstVoice() {
        if (cards.selection.firstVoiceAuto) VoicePlayer.play(context, cards.selection.firstVoice)
    }

    /** Ensin valmis: Sitten-kortista tulee viimeinen toiminto, tai kulku päättyy. */
    fun afterFirst() {
        if (cards.promoteNext()) {
            page = AppPage.LAST
            playFirstVoice()
        } else {
            page = AppPage.DONE
        }
    }

    // P0-21e: käynnissä olevan ajastimen pysäyttäminen on lukittuna ohjaajan toiminto.
    val guardTimerStop: (() -> Unit) -> Unit = { action ->
        if (timer.running && caregiver.isLocked) caregiver.run(AppAction.PAUSE_TIMER, action) else action()
    }

    val goBack: () -> Unit = {
        if (page == AppPage.TIMER && timer.activeTask.isNotBlank()) {
            guardTimerStop {
                timer.stopKeepingRemaining()
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
                    if (destination == AppPage.TIMER) timer.activeTask = ""
                }

                AppPage.TIMER -> TimerScreen(
                    remaining = timer.remaining,
                    duration = timer.duration,
                    running = timer.running,
                    showLastTen = showLastTen,
                    activeTask = timer.activeTask,
                    firstPhoto = sel.firstPhoto,
                    nextPhoto = sel.nextPhoto,
                    nextActivity = nextShown,
                    onGoNext = {
                        timer.finishActivity()
                        afterFirst()
                    },
                    onShowLastTen = { showLastTen = it },
                    onPreset = { minutes -> timer.setMinutes(minutes) },
                    onStartPause = {
                        timer.startPause(context.getString(R.string.ajastin)) { playFirstVoice() }
                    },
                    onReset = { timer.reset() }
                )

                AppPage.FIRST_THEN -> FirstThenScreen(
                    first = firstShown,
                    next = nextShown,
                    firstPhoto = sel.firstPhoto,
                    nextPhoto = sel.nextPhoto,
                    firstDescription = sel.firstDescription,
                    nextDescription = sel.nextDescription,
                    firstVoice = sel.firstVoice,
                    nextVoice = sel.nextVoice,
                    firstVoiceAuto = sel.firstVoiceAuto,
                    nextVoiceAuto = sel.nextVoiceAuto,
                    onFirst = { name -> cards.update { it.copy(firstName = chosen(name)) } },
                    onNext = { name -> cards.update { it.copy(nextName = chosen(name)) } },
                    onFirstPhoto = { path -> cards.update { it.copy(firstPhoto = path) } },
                    onNextPhoto = { path -> cards.update { it.copy(nextPhoto = path) } },
                    onFirstDescription = { text -> cards.update { it.copy(firstDescription = text) } },
                    onNextDescription = { text -> cards.update { it.copy(nextDescription = text) } },
                    onFirstVoice = { path, auto -> cards.update { it.copy(firstVoice = path, firstVoiceAuto = auto) } },
                    onNextVoice = { path, auto -> cards.update { it.copy(nextVoice = path, nextVoiceAuto = auto) } },
                    onDone = {
                        guardTimerStop {
                            timer.stopForManual()
                            page = AppPage.MANUAL
                            playFirstVoice()
                        }
                    },
                    onStartTimer = { minutes ->
                        guardTimerStop {
                            timer.prepareForActivity(firstShown, minutes)
                            page = AppPage.TIMER
                        }
                    }
                )

                AppPage.PROGRAMS -> ProgramsScreen()

                AppPage.CAREGIVER -> CaregiverSettingsScreen()

                AppPage.MANUAL -> ManualFlowScreen(
                    first = firstShown,
                    firstPhoto = sel.firstPhoto,
                    next = nextShown,
                    nextPhoto = sel.nextPhoto,
                    onNext = {
                        timer.markStopped()
                        if (cards.promoteNext()) {
                            page = AppPage.LAST
                            playFirstVoice()
                        }
                    }
                )

                AppPage.LAST -> LastActivityScreen(
                    name = firstShown,
                    imagePath = sel.firstPhoto,
                    onFinish = {
                        timer.markStopped()
                        page = AppPage.DONE
                    },
                    onAddNext = if (caregiver.allows(AppAction.EDIT_CARDS)) {
                        {
                            cards.clearNext()
                            page = AppPage.FIRST_THEN
                        }
                    } else null
                )

                AppPage.DONE -> FlowDoneScreen(
                    onHome = {
                        timer.activeTask = ""
                        page = AppPage.HOME
                    }
                )
            }

            if (page != AppPage.HOME &&
                page != AppPage.FIRST_THEN &&
                page != AppPage.PROGRAMS &&
                page != AppPage.LAST &&
                page != AppPage.DONE &&
                !(page == AppPage.TIMER && timer.activeTask.isNotBlank())) {
                TextButton(
                    onClick = goBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
    if (page == AppPage.TIMER && timer.activeTask.isNotBlank())
        stringResource(R.string.takaisin_ensin_sitten)
    else stringResource(R.string.takaisin_aloitukseen),
    fontSize = 16.sp
)
                }
            }
        }
    }
}
