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
import fi.aikapolku.app.backup.ImportLaunch
import fi.aikapolku.app.week.ChangeScreen
import fi.aikapolku.app.week.ClientActivity
import fi.aikapolku.app.week.QuickChangeScreen
import fi.aikapolku.app.week.FlowChangeScreen
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
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
internal enum class AppPage { HOME, TIMER, FIRST_THEN, PROGRAMS, CAREGIVER, MANUAL, LAST, DONE, EVENT_CARD, DAY, CHANGE, QUICK_CHANGE, FLOW_CHANGE }

/** Sovelluksen navigointi ja Ensin–Sitten-kulun yhteinen tila. */
@Composable
internal fun AikaPolkuApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardStore = remember(context) { CardStore(context.applicationContext) }
    var page by rememberSaveable {
        // Asiakkaan vaihto Asetuksista luo tilan uudelleen: palataan Asetuksiin.
        mutableStateOf(if (fi.aikapolku.app.profiles.ProfileSession.takeReopenSettings()) AppPage.CAREGIVER else AppPage.HOME)
    }
    val caregiver = LocalCaregiver.current

    val timerPort = remember(context) { BackgroundTimerPort(context.applicationContext, "timer") }
    val timer = rememberSaveable(saver = FlowTimer.saver(timerPort)) { FlowTimer(timerPort) }
    val cards = rememberSaveable(saver = FirstThenCards.saver) { FirstThenCards(cardStore.loadSelection()) }
    val sel = cards.selection
    // Viikko-ohjelman tapahtuma, joka avataan aloitusnäkymän Tänään-listasta.
    var pendingProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEventDate by rememberSaveable { mutableStateOf<String?>(null) }
    var eventCardName by rememberSaveable { mutableStateOf("") }
    var eventCardPhoto by rememberSaveable { mutableStateOf("") }
    var eventCardId by rememberSaveable { mutableStateOf("") }
    var eventCardDate by rememberSaveable { mutableStateOf("") }
    var eventCardOngoing by rememberSaveable { mutableStateOf(false) }
    var changeId by rememberSaveable { mutableStateOf("") }
    var programsWeek by rememberSaveable { mutableStateOf(false) }
    // Pikamuutos: valmiiksi valittu päivä ja tapahtuma (koko päivän näkymän ⋮-valikosta).
    var quickDayOffset by rememberSaveable { mutableIntStateOf(0) }
    var quickEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var quickReturn by rememberSaveable { mutableStateOf(AppPage.HOME) }
    // Ensin–Sitten: kesken kulun vaihdettu Sitten-kortti (vanha nimi ja kuva).
    var flowOldName by rememberSaveable { mutableStateOf("") }
    var flowOldPhoto by rememberSaveable { mutableStateOf("") }
    var askFlowChange by rememberSaveable { mutableStateOf(false) }

    // Ilmoituksesta avattu tapahtuma avataan, jos se kuuluu nykyiseen profiiliin.
    val launch = EventLaunch.pending.value
    LaunchedEffect(launch) {
        val request = launch ?: return@LaunchedEffect
        EventLaunch.pending.value = null
        if (request.profileId != ProfileStore.currentId(context)) {
            page = AppPage.HOME
            return@LaunchedEffect
        }
        val original = fi.aikapolku.app.data.ClientWeek.load(context).event(request.eventId)
        // Päivän muutos: kuittaamaton muutos näytetään ensin, muuten avataan voimassa oleva tapahtuma.
        val change = DayChanges.wholeChange(ChangeStore(context).load(), request.date, request.eventId)
        val event = if (original != null && change != null) DayChanges.effective(original, change) else original
        when {
            event == null -> page = AppPage.HOME
            change != null && (change.type == ChangeType.CANCEL ||
                AckStore(context).ackTime(change.date, change.ackId) == null) -> {
                changeId = change.id
                page = AppPage.CHANGE
            }
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
                eventCardOngoing = event.ongoing
                page = AppPage.EVENT_CARD
            }
        }
    }

    // Sähköpostin liitteestä avattu vientitiedosto: tuonti on ohjaajan toiminto (PIN).
    val importUri = ImportLaunch.pending.value
    LaunchedEffect(importUri) {
        if (importUri != null) caregiver.run(AppAction.SETTINGS) {
            fi.aikapolku.app.caregiver.RoleSession.toCaregiver()
            page = AppPage.CAREGIVER
        }
    }

    // Asiakkaaksi vaihdettaessa ohjaajan näkymistä palataan aloitukseen.
    val isCaregiver = caregiver.isCaregiver
    LaunchedEffect(isCaregiver) {
        if (!isCaregiver && (page == AppPage.CAREGIVER || page == AppPage.QUICK_CHANGE)) page = AppPage.HOME
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
    // Ajastin ja Ensin–Sitten: näyttö päällä ajan käydessä (ks. ScreenOn).
    fi.aikapolku.app.timer.KeepScreenOnWhile("timer", timer.running)

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
        if (!caregiver.isCaregiver && page != AppPage.FLOW_CHANGE) {
            // Asiakkaalla takaisin vie aina omaan päivään (NYT-kortti kertoo, mikä on kesken).
            page = AppPage.HOME
        } else if (page == AppPage.TIMER && timer.activeTask.isNotBlank()) {
            page = AppPage.FIRST_THEN
        } else if (page == AppPage.MANUAL) {
            page = AppPage.FIRST_THEN
        } else if (page == AppPage.FLOW_CHANGE) {
            page = if (timer.inProgress) AppPage.TIMER else AppPage.FIRST_THEN
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
        eventCardOngoing = event.ongoing
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
                        AppPage.CHANGE -> stringResource(R.string.change_title)
                        AppPage.QUICK_CHANGE -> stringResource(R.string.quick_title)
                        AppPage.FLOW_CHANGE -> stringResource(R.string.change_title)
                        AppPage.HOME -> ""
                    },
                    onBack = { (backOverride.value ?: goBack)() },
                    // Lukko aina samassa paikassa; ohjaajan asetuksissa lukko on jo auki.
                    showLock = caregiver.isLocked && !caregiver.testMode && page != AppPage.CAREGIVER,
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
            if (askFlowChange) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { askFlowChange = false },
                    title = { Text(stringResource(R.string.flow_change_ask_title)) },
                    text = { Text(stringResource(R.string.flow_change_ask_text, flowOldName, nextShown)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            askFlowChange = false
                            page = AppPage.FLOW_CHANGE
                        }) { Text(stringResource(R.string.flow_change_show)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { askFlowChange = false }) { Text(stringResource(R.string.flow_change_no)) }
                    }
                )
            }
            // Asiakkaan etusivulla kesken oleva toiminta näkyy NYT-kortissa, joten paluurivi vain ohjaajalle.
            if (timer.inProgress && (page == AppPage.FIRST_THEN || (page == AppPage.HOME && caregiver.isCaregiver))) {
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
                    onOpenDay = { page = AppPage.DAY },
                    onOpenChange = {
                        changeId = it.id
                        page = AppPage.CHANGE
                    },
                    onMakeChange = { id ->
                        quickDayOffset = 0
                        quickEventId = id
                        quickReturn = AppPage.HOME
                        page = AppPage.QUICK_CHANGE
                    },
                    onOpenWeek = {
                        programsWeek = true
                        page = AppPage.PROGRAMS
                    },
                    // Ohjaajan valmistelema Ensin–Sitten (aika asetettu) tai käynnissä oleva ajastin
                    // näkyy asiakkaan NYT-kortissa.
                    activity = when {
                        timer.activeTask.isNotBlank() -> ClientActivity.FirstThen(
                            first = firstShown, firstPhoto = sel.firstPhoto,
                            next = nextShown, nextPhoto = sel.nextPhoto,
                            started = timer.inProgress
                        )
                        timer.running -> ClientActivity.Timer(stringResource(R.string.timer_title))
                        else -> null
                    },
                    // Asiakkaan yksi painallus: valmisteltu Ensin–Sitten-aika käynnistyy heti ja
                    // kellotaulu aukeaa (aika jo käynnissä). Kesken oleva vain avataan.
                    onOpenActivity = {
                        if (timer.activeTask.isNotBlank() && !timer.running && timer.remaining > 0L && !timer.inProgress) {
                            timer.startPause(context.getString(R.string.timer_title)) { playFirstVoice() }
                        }
                        page = AppPage.TIMER
                    }
                )

                AppPage.DAY -> DayScreen(
                    onOpenProgram = { openProgramEvent(it) },
                    onOpenCard = { openCardEvent(it) },
                    onOpenChange = {
                        changeId = it.id
                        page = AppPage.CHANGE
                    },
                    onMakeChange = { offset, id ->
                        quickDayOffset = offset
                        quickEventId = id
                        quickReturn = AppPage.DAY
                        page = AppPage.QUICK_CHANGE
                    }
                )

                AppPage.QUICK_CHANGE -> key(quickEventId, quickDayOffset) {
                    QuickChangeScreen(quickDayOffset, quickEventId) {
                        val back = quickReturn
                        quickEventId = null
                        quickDayOffset = 0
                        quickReturn = AppPage.HOME
                        page = back
                    }
                }

                AppPage.FLOW_CHANGE -> FlowChangeScreen(flowOldName, flowOldPhoto, nextShown, sel.nextPhoto) {
                    // Takaisin käynnissä olevaan ajastukseen, jos se on kesken.
                    page = if (timer.inProgress) AppPage.TIMER else AppPage.FIRST_THEN
                }

                AppPage.CHANGE -> ChangeScreen(changeId) { page = AppPage.HOME }

                AppPage.EVENT_CARD -> EventCardScreen(
                    ongoing = eventCardOngoing,
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
                    activeTask = timer.activeTask,
                    firstPhoto = sel.firstPhoto,
                    nextPhoto = sel.nextPhoto,
                    nextActivity = nextShown,
                    onGoNext = {
                        timer.finishActivity()
                        afterFirst()
                    },
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
                    onNext = { name ->
                        // Kesken kulun vaihdettu Sitten-kortti voidaan näyttää käyttäjälle muutoksena.
                        val old = cards.selection
                        if (timer.inProgress && old.nextName.isNotBlank() && chosen(name).isNotBlank() && chosen(name) != old.nextName) {
                            flowOldName = old.nextName
                            flowOldPhoto = old.nextPhoto
                            askFlowChange = true
                        }
                        cards.update { it.copy(nextName = chosen(name)) }
                    },
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
                    onExitToHome = { page = AppPage.HOME },
                    initialWeekTab = programsWeek.also { programsWeek = false },
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
