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
import fi.aikapolku.app.timer.TimerReturnRow
import fi.aikapolku.app.timer.playCompletionSound
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppTopBar
import fi.aikapolku.app.ui.LocalTitleOverride
import fi.aikapolku.app.ui.LocalBackOverride
import fi.aikapolku.app.ui.LocalTopBarAction
import fi.aikapolku.app.week.dayName
import fi.aikapolku.app.voice.VoicePlayer
import fi.aikapolku.app.week.EventCardScreen
import fi.aikapolku.app.week.DayScreen
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.week.EventLaunch
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekStore
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sovelluksen näkymät. */
internal enum class AppPage { HOME, TIMER, FIRST_THEN, PROGRAMS, CAREGIVER, MANUAL, LAST, DONE, EVENT_CARD, DAY }

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
    // Viikko-ohjelman tapahtuma, joka avataan aloitusnäkymän Tänään-listasta.
    var pendingProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEventDate by rememberSaveable { mutableStateOf<String?>(null) }
    var eventCardName by rememberSaveable { mutableStateOf("") }
    var eventCardPhoto by rememberSaveable { mutableStateOf("") }
    var eventCardId by rememberSaveable { mutableStateOf("") }
    var eventCardDate by rememberSaveable { mutableStateOf("") }

    // Ilmoituksesta avattu tapahtuma avataan, jos se kuuluu nykyiseen profiiliin.
    val launch = EventLaunch.pending.value
    LaunchedEffect(launch) {
        val request = launch ?: return@LaunchedEffect
        EventLaunch.pending.value = null
        if (request.profileId != ProfileStore.currentId(context)) {
            page = AppPage.HOME
            return@LaunchedEffect
        }
        val event = WeekStore(context).load().events.firstOrNull { it.id == request.eventId }
        when {
            event == null -> page = AppPage.HOME
            event.kind == EventKind.PROGRAM -> {
                pendingProgramId = event.programId
                pendingEventId = event.id
                pendingEventDate = request.date.toString()
                page = AppPage.PROGRAMS
            }
            else -> {
                eventCardName = event.cardName
                eventCardPhoto = event.cardPhoto
                eventCardId = event.id
                eventCardDate = request.date.toString()
                page = AppPage.EVENT_CARD
            }
        }
    }

    // Valinnat tallennetaan aina niiden muuttuessa.
    LaunchedEffect(sel) { cardStore.saveSelection(sel) }
    // Ensin–Sitten-kulku päättyy: alkuperäinen pari takaisin (ei kahta samaa korttia).
    // Ei FIRST_THEN-sivulla, koska "Lisää vaihe" palaa sinne kesken kulun.
    LaunchedEffect(page) {
        if (page == AppPage.DONE || page == AppPage.HOME) cards.finishFlow()
    }

    // Näkymät näyttävät valitsemattoman kortin tekstinä "Valitse toiminto".
    val placeholder = stringResource(R.string.choose_activity)
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

    // Takaisin ei pysäytä aikaa: ajastin jatkuu taustalla ja siihen palataan
    // "Ajastin käynnissä" -riviltä. Tauko tehdään vain taukopainikkeella.
    val goBack: () -> Unit = {
        if (page == AppPage.TIMER && timer.activeTask.isNotBlank()) {
            page = AppPage.FIRST_THEN
        } else if (page == AppPage.MANUAL) {
            page = AppPage.FIRST_THEN
        } else {
            page = AppPage.HOME
        }
    }

    // Viikko-ohjelman tapahtuman avaus Tänään-listasta tai koko päivän näkymästä.
    fun openProgramEvent(event: WeekEvent) {
        pendingProgramId = event.programId
        pendingEventId = event.id
        pendingEventDate = LocalDate.now().toString()
        page = AppPage.PROGRAMS
    }

    fun openCardEvent(event: WeekEvent) {
        eventCardName = event.cardName
        eventCardPhoto = event.cardPhoto
        eventCardId = event.id
        eventCardDate = LocalDate.now().toString()
        page = AppPage.EVENT_CARD
    }

    BackHandler(enabled = page != AppPage.HOME) {
        goBack()
    }

    val rootScroll = rememberScrollState()
    LaunchedEffect(page) { rootScroll.scrollTo(0) }
    // Sisäkkäisen näkymän (esim. muokkain) oma otsikko yläpalkkiin.
    val titleOverride = remember { mutableStateOf<String?>(null) }
    val backOverride = remember { mutableStateOf<(() -> Unit)?>(null) }
    val topBarAction = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    LaunchedEffect(page) {
        titleOverride.value = null
        backOverride.value = null
        // topBarAction tyhjenee näkymän poistuessa (TopBarAction/onDispose). Tässä nollaus
        // ajautuisi uuden näkymän asetuksen jälkeen ja piilottaisi sen.
    }

    CompositionLocalProvider(LocalTitleOverride provides titleOverride, LocalBackOverride provides backOverride, LocalTopBarAction provides topBarAction) {
    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            if (page != AppPage.HOME) {
                AppTopBar(
                    title = titleOverride.value ?: when (page) {
                        AppPage.TIMER -> stringResource(R.string.timer_title)
                        AppPage.FIRST_THEN, AppPage.MANUAL, AppPage.LAST, AppPage.DONE -> stringResource(R.string.ensin_sitten)
                        AppPage.PROGRAMS -> stringResource(R.string.paivaohjelmat)
                        AppPage.CAREGIVER -> stringResource(R.string.caregiver_settings_title)
                        AppPage.DAY -> dayName(LocalDate.now().dayOfWeek.value)
                        AppPage.EVENT_CARD -> dayName(LocalDate.now().dayOfWeek.value)
                        AppPage.HOME -> ""
                    },
                    onBack = { (backOverride.value ?: goBack)() },
                    // Lukko aina samassa paikassa; ohjaajan asetuksissa lukko on jo auki.
                    showLock = caregiver.lockEnabled && caregiver.isLocked && page != AppPage.CAREGIVER,
                    onUnlock = { caregiver.unlock() },
                    action = topBarAction.value
                )
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
            if ((page == AppPage.HOME || page == AppPage.FIRST_THEN) && timer.inProgress) {
                TimerReturnRow(
                    task = timer.activeTask,
                    photo = sel.firstPhoto,
                    remaining = timer.remaining,
                    running = timer.running,
                    onClick = { page = AppPage.TIMER }
                )
            }
            when (page) {
                AppPage.HOME -> HomeScreen(
                    open = { destination ->
                        page = destination
                        // Kesken oleva Ensin-ajastus säilyy; muuten ajastin avataan yksinään.
                        if (destination == AppPage.TIMER && !timer.inProgress) timer.activeTask = ""
                    },
                    onOpenProgram = { openProgramEvent(it) },
                    onOpenCard = { openCardEvent(it) },
                    onOpenDay = { page = AppPage.DAY }
                )

                AppPage.DAY -> DayScreen(
                    onOpenProgram = { openProgramEvent(it) },
                    onOpenCard = { openCardEvent(it) }
                )

                AppPage.EVENT_CARD -> EventCardScreen(
                    name = eventCardName,
                    photo = eventCardPhoto,
                    doneAt = remember(eventCardId, eventCardDate) {
                        val date = runCatching { LocalDate.parse(eventCardDate) }.getOrElse { LocalDate.now() }
                        eventCardId.takeIf { it.isNotBlank() }?.let { AckStore(context).ackTime(date, it) }
                    },
                    onDone = {
                        // Valmis kuittaa tapahtuman (näkyy Tänään-listassa ja ohjaajalle).
                        if (eventCardId.isNotBlank()) {
                            val date = runCatching { LocalDate.parse(eventCardDate) }.getOrElse { LocalDate.now() }
                            AckStore(context).ack(date, eventCardId)
                        }
                        page = AppPage.HOME
                    }
                )

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
                        timer.startPause(context.getString(R.string.timer_title)) { playFirstVoice() }
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
                    timerInProgress = timer.inProgress,
                    onReturnToTimer = { page = AppPage.TIMER },
                    onStartTimer = { minutes ->
                        guardTimerStop {
                            timer.prepareForActivity(firstShown, minutes)
                            page = AppPage.TIMER
                        }
                    }
                )

                AppPage.PROGRAMS -> ProgramsScreen(
                    startProgramId = pendingProgramId,
                    startEventId = pendingEventId,
                    startEventDate = pendingEventDate,
                    onStartHandled = {
                        pendingProgramId = null
                        pendingEventId = null
                        pendingEventDate = null
                    }
                )

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

            // Takaisin vain yläreunasta: alareunan "Takaisin aloitukseen" poistettu toistona.
        }
    }
    }
}
