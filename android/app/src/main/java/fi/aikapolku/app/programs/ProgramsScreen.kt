package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.SubstepDefaults
import fi.aikapolku.app.week.StepChangeDialog
import fi.aikapolku.app.week.replacementStep
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.TimeMode
import fi.aikapolku.app.data.parseDraft
import fi.aikapolku.app.data.serializeDraft
import fi.aikapolku.app.programs.editor.ProgramEditorScreen
import fi.aikapolku.app.programs.run.PrefsRunProgress
import fi.aikapolku.app.programs.run.DoneGate
import fi.aikapolku.app.programs.run.ProgramRunController
import fi.aikapolku.app.programs.run.RunNext
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.RunTime
import fi.aikapolku.app.programs.run.SupportMessageScreen
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.timer.BackgroundTimerPort
import fi.aikapolku.app.timer.Tick
import fi.aikapolku.app.timer.TimeModeChoices
import fi.aikapolku.app.timer.playCompletionSound
import fi.aikapolku.app.voice.VoicePlayer
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.SegmentedChoice
import fi.aikapolku.app.ui.ScreenTitle
import fi.aikapolku.app.week.WeekView
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val programsTeal = androidx.compose.ui.graphics.Color(0xFF0F766E)

// The program and its monotonic countdown deadline survive configuration changes.
private val programDraftSaver = Saver<DayProgram, String>(
    save = { serializeDraft(it) },
    restore = { parseDraft(it) }
)

/** Päiväohjelmien näkymät. */
internal enum class ProgramsView { LIST, EDIT, RUN, DONE }

/** Luettelon välilehdet: ohjelmat ja viikko-ohjelma. */
internal enum class ProgramsTab { PROGRAMS, WEEK }

@Composable
private fun ProgramsTabs(selected: ProgramsTab, onSelect: (ProgramsTab) -> Unit) {
    SegmentedChoice(
        options = listOf(stringResource(R.string.tab_programs), stringResource(R.string.tab_week)),
        selected = selected.ordinal,
        onSelect = { onSelect(ProgramsTab.entries[it]) }
    )
}

@Composable
internal fun ProgramsScreen(
    /** Tänään-listasta käynnistettävä päiväohjelma. */
    startProgramId: String? = null,
    /** Viikko-ohjelman tapahtuma (tunnus ja päivä), joka kuitataan, kun ohjelma viedään loppuun. */
    startEventId: String? = null,
    startEventDate: String? = null,
    onStartHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember(context) {
        ProgramStore(context.applicationContext)
    }
    val completionSoundScope = rememberCoroutineScope()
    val runProgress = remember(context) { PrefsRunProgress(context.applicationContext) }
    val runTimer = remember(context) { BackgroundTimerPort(context.applicationContext, "program") }
    val playVoice: (String) -> Unit = remember(context) { { path -> VoicePlayer.play(context, path) } }
    val initialPrograms = remember(store) { store.load() }
    var programs by remember { mutableStateOf(initialPrograms) }
    // Keskeytynyt ohjelma jatkuu, jos se on yhä tallessa.
    // Tämän päivän vaihemuutokset mukana, jotta keskeytynyt ohjelma jatkuu samasta vaiheesta.
    val resumable = remember {
        initialPrograms.firstOrNull { it.id == runProgress.programId && it.steps.isNotEmpty() }?.let { withTodayChanges(context, it) }
    }
    val run = rememberSaveable(saver = ProgramRunController.saver(runTimer, runProgress, playVoice)) {
        ProgramRunController(runTimer, runProgress, playVoice).also { c -> resumable?.let { c.resume(it) } }
    }
    var view by rememberSaveable {
        mutableStateOf(if (resumable != null) ProgramsView.RUN else ProgramsView.LIST)
    }

    // Muokattava ohjelma (uusi, pohjasta tai olemassa oleva). Suoritettava ohjelma on [run].
    var draft by rememberSaveable(stateSaver = programDraftSaver) {
        mutableStateOf(DayProgram(UUID.randomUUID().toString(), "", emptyList()))
    }

    var showFullProgram by rememberSaveable { mutableStateOf(false) }
    var showRunActions by remember { mutableStateOf(false) }
    var stepChangeOpen by remember { mutableStateOf(false) }

    // P0-21e: ohjaajan lukitus. Käynnistys, tulostus, apu, tauko, Valmis ja ohitus ovat aina vapaita.
    val caregiver = LocalCaregiver.current
    val canEditPrograms = caregiver.allows(AppAction.EDIT_PROGRAMS)
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showRunSettings by remember { mutableStateOf(false) }
    var nextPreviewMode by rememberSaveable {
        mutableStateOf(RunPreferences.nextMode(context))
    }
    val programmeScroll = rememberScrollState()
    var deleteTarget by remember { mutableStateOf<DayProgram?>(null) }
    // Yhden päiväohjelman vienti tiedostoksi (ohjelman ⋮-valikosta).
    var exportTarget by remember { mutableStateOf<String?>(null) }
    exportTarget?.let { id ->
        fi.aikapolku.app.backup.ExportDialog(
            initialScope = fi.aikapolku.app.backup.BackupScope.PROGRAMS,
            initialProgramIds = setOf(id)
        ) { exportTarget = null }
    }
    var timeSettingsOpen by remember { mutableStateOf(false) }

    // Jos lukitus menee päälle, ohjaajan ikkunat suljetaan. Muokkausnäkymä jätetään
    // tarkoituksella auki, jotta tallentamattomat muutokset eivät katoa.
    LaunchedEffect(caregiver.isLocked) {
        if (caregiver.isLocked) {
            timeSettingsOpen = false
            showRunSettings = false
            showExitConfirmation = false
            deleteTarget = null
        }
    }
    var timeMode by rememberSaveable {
        mutableStateOf(TimeDisplayPrefs.load(context))
    }

    fun startProgram(program: DayProgram) {
        if (program.steps.isEmpty()) return
        showFullProgram = false
        run.start(withTodayChanges(context, program))
        view = ProgramsView.RUN
    }

    // Tänään-listasta avattu ohjelma käynnistyy heti (tai jatkuu, jos sama ohjelma on jo kesken).
    var runEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var runEventDate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(startProgramId) {
        val id = startProgramId ?: return@LaunchedEffect
        runEventId = startEventId
        runEventDate = startEventDate
        onStartHandled()
        val program = programs.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (view == ProgramsView.RUN && run.program.id == id) return@LaunchedEffect
        startProgram(program)
    }

    var tab by rememberSaveable { mutableStateOf(ProgramsTab.PROGRAMS) }

    var confirmDoneLeft by remember { mutableStateOf<Long?>(null) }

    fun finishRun() {
        view = ProgramsView.DONE
        // Tänään-listasta käynnistetty ohjelma merkitään tehdyksi.
        runEventId?.let { id ->
            val date = runEventDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: java.time.LocalDate.now()
            fi.aikapolku.app.data.AckStore(context.applicationContext).ack(date, id)
        }
        runEventId = null
        runEventDate = null
    }

    fun skipTask() {
        if (run.skip()) finishRun()
    }

    // Valmis: vahinkonaputusten esto ja ohjaajan valitsema toiminta, kun aikaa on jäljellä.
    fun onDonePressed() {
        if (run.tapGuardActive()) return
        when (run.doneGate(RunPreferences.doneMode(context), caregiverOpen = !caregiver.isLocked && caregiver.lockEnabled)) {
            DoneGate.ALLOW -> if (run.next()) finishRun()
            DoneGate.CONFIRM -> confirmDoneLeft = run.timeLeft()
            DoneGate.START_TIMER -> run.startTimer()
            DoneGate.WAIT -> Unit
        }
    }

    // Aloita ja Jatka ovat aina vapaita; Tauko lukittuna vain PIN-koodilla.
    fun toggleStepTimer() {
        when {
            run.running && caregiver.isLocked -> caregiver.run(AppAction.PAUSE_TIMER) { run.pause() }
            run.running -> run.pause()
            else -> run.startTimer()
        }
    }

    LaunchedEffect(view, run.running, run.deadline, run.stepIndex) {
        if (view == ProgramsView.RUN && run.running) {
            while (true) {
                when (run.tick()) {
                    Tick.RUNNING -> delay(150L)
                    Tick.ENDED -> {
                        val mode = BackgroundTimer.selectedSound(context)
                        if (mode != "off") completionSoundScope.launch { playCompletionSound(mode) }
                        break
                    }
                    Tick.ENDED_SILENT -> break
                }
            }
        }
    }

    // No scrolling through old tasks after the user completes an action.
    // Snap immediately rather than animating, to avoid unnecessary motion.
    LaunchedEffect(view, run.stepIndex, run.subStepIndex) {
        programmeScroll.scrollTo(0)
    }

    // Suorituksen aikana yläpalkissa ohjelman nimi.
    if (view == ProgramsView.RUN || view == ProgramsView.DONE) ScreenTitle(run.program.name)

    Box(modifier = Modifier.fillMaxSize()) {
        val runStep = if (view == ProgramsView.RUN) run.currentStep else null
        if (runStep != null) {
            val kind = run.support
            if (kind != null) {
                SupportMessageScreen(
                    kind = kind,
                    timerPaused = run.resumeAfterSupport,
                    onContinue = { run.continueAfterSupport() },
                    onClose = { run.closeSupport() }
                )
            } else {
                val nextSubStep = runStep.subSteps.getOrNull(run.subStepIndex + 1)
                val nextTask = run.nextStep
                RunStepScreen(
                    taskName = runStep.name,
                    taskPhoto = runStep.photo,
                    description = runStep.description,
                    subSteps = runStep.subSteps,
                    subStepIndex = run.subStepIndex,
                    taskIndex = run.activeIndex,
                    taskCount = run.activeCount,
                    changedFrom = runStep.changedFrom.takeIf { runStep.changedToday },
                    time = when (runStep.mode) {
                        StepMode.DURATION -> RunTime.Countdown(
                            remainingMs = run.remaining,
                            durationMs = maxOf(runStep.minutes * 60_000L, run.remaining),
                            running = run.running,
                            displayMode = timeMode,
                            pauseLocked = run.running && caregiver.isLocked
                        )
                        StepMode.CLOCK -> RunTime.Clock(runStep.clock)
                        StepMode.MANUAL -> null
                    },
                    // Esikatselu: ensin seuraava osavaihe, viimeisessä osavaiheessa seuraava tehtävä.
                    next = when {
                        nextSubStep != null -> RunNext(nextSubStep, runStep.name, runStep.photo, isSubStep = true)
                        nextTask != null -> RunNext(nextTask.name, nextTask.name, nextTask.photo, isSubStep = false)
                        else -> null
                    },
                    nextMode = nextPreviewMode,
                    onToggleTimer = { toggleStepTimer() },
                    onHelp = { run.requestSupport(SupportKind.HELP) },
                    onBreak = { run.requestSupport(SupportKind.BREAK) },
                    onDone = { onDonePressed() },
                    onMore = { showRunActions = true },
                    doneLabel = when (run.doneGate(RunPreferences.doneMode(context), caregiverOpen = !caregiver.isLocked && caregiver.lockEnabled)) {
                        DoneGate.START_TIMER -> stringResource(R.string.done_start_timer)
                        DoneGate.WAIT -> stringResource(R.string.done_wait, formatLeft(run.timeLeft()))
                        else -> null
                    },
                    doneEnabled = run.doneGate(RunPreferences.doneMode(context), caregiverOpen = !caregiver.isLocked && caregiver.lockEnabled) != DoneGate.WAIT
                )
            }
        } else if (view == ProgramsView.EDIT) {
            // P0-23: nelivaiheinen muokkain (ProgramEditor.kt) omana näkymänään.
            ProgramEditorScreen(
                draft = draft,
                onDraftChange = { draft = it },
                otherProgramNames = programs.filter { it.id != draft.id }.map { it.name },
                onSave = { edited ->
                    store.save(ProgramTiming.apply(edited).copy(name = edited.name.trim()))
                    programs = store.load()
                    view = ProgramsView.LIST
                    // Kerran: "Käyttääkö tätä laitetta myös joku muu?"
                    caregiver.offerLockOnce()
                },
                onCancel = { view = ProgramsView.LIST }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(programmeScroll)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
    when (view) {
        ProgramsView.LIST -> {
            ProgramsTabs(tab) { tab = it }
            if (tab == ProgramsTab.WEEK) WeekView(programs = programs, canEdit = canEditPrograms)
            else ProgramListView(
            programs = programs,
            canEdit = canEditPrograms,
            onNew = {
                draft = DayProgram(UUID.randomUUID().toString(), "", emptyList(), timeMode = TimeMode.FREE)
                view = ProgramsView.EDIT
            },
            onTemplate = { template ->
                draft = ProgramTiming.infer(RoutineTemplates.create(template) { context.getString(it) })
                view = ProgramsView.EDIT
            },
            onStart = { startProgram(it) },
            onPrint = { ProgramPrint.open(context, it) },
            onShare = { ProgramPrint.shareImage(context, it) },
            onExport = { exportTarget = it.id },
            onEdit = {
                draft = ProgramTiming.infer(it)
                view = ProgramsView.EDIT
            },
            onDelete = { deleteTarget = it }
        )
        }

        ProgramsView.EDIT -> Unit // Muokkain piirretään yllä (ProgramEditorScreen).

        ProgramsView.RUN -> Unit // Suoritus piirretään yllä omana näkymänään (RunStepScreen).

        ProgramsView.DONE -> ProgramDoneView(run.program, run.skipped, onBack = { view = ProgramsView.LIST })
    }
        } // Scrollable Column

        } // Ei suoritusta
    } // Content Box

    if (showRunActions && view == ProgramsView.RUN) {
        val current = run.currentStep
        val fallbackTitle = stringResource(R.string.timer_title)
        RunActionsDialog(
            canExtend = current?.mode == StepMode.DURATION && caregiver.allows(AppAction.EXTEND_TIME),
            canChangeSettings = caregiver.allows(AppAction.SETTINGS),
            canEnd = caregiver.allows(AppAction.END_PROGRAM),
            onExtend = {
                run.extend(5 * 60_000L, fallbackTitle)
                showRunActions = false
            },
            onTimeSettings = { showRunActions = false; timeSettingsOpen = true },
            onViewSettings = { showRunActions = false; showRunSettings = true },
            onShowProgram = { showRunActions = false; showFullProgram = true },
            onSkip = { showRunActions = false; skipTask() },
            onEnd = { showRunActions = false; showExitConfirmation = true },
            onDismiss = { showRunActions = false },
            onStepChange = if (canEditPrograms) {
                { showRunActions = false; stepChangeOpen = true }
            } else null
        )
    }

    // Ohjaajan muutos nykyiseen vaiheeseen kesken suorituksen.
    if (stepChangeOpen && view == ProgramsView.RUN) {
        StepChangeDialog(
            onCancelStep = {
                stepChangeOpen = false
                run.currentStep?.let { saveRunStepChange(context, run.program, it, runEventId, null) }
                skipTask()
            },
            onReplace = { card ->
                stepChangeOpen = false
                val current = run.currentStep
                if (current != null) {
                    saveRunStepChange(context, run.program, current, runEventId, card)
                    val texts: (Int) -> String = { context.getString(it) }
                    run.replaceCurrent(replacementStep(current, card, SubstepDefaults.forCard(card.name, texts, card.photoPath)))
                }
            },
            onDismiss = { stepChangeOpen = false }
        )
    }

    confirmDoneLeft?.let { left ->
        AlertDialog(
            onDismissRequest = { confirmDoneLeft = null },
            title = { Text(stringResource(R.string.done_confirm_title, formatLeft(left))) },
            text = { Text(stringResource(R.string.done_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDoneLeft = null
                    if (run.next()) finishRun()
                }) { Text(stringResource(R.string.done_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDoneLeft = null }) { Text(stringResource(R.string.done_confirm_no)) }
            }
        )
    }

    if (showFullProgram && view == ProgramsView.RUN) {
        AlertDialog(
            onDismissRequest = { showFullProgram = false },
            title = { Text(stringResource(R.string.koko_paivaohjelma)) },
            text = { ProgramOverview(run.program.steps.filterNot { it.cancelledToday }, run.activeIndex) },
            confirmButton = {
                TextButton(onClick = { showFullProgram = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.lopetetaanko_paivaohjelma)) },
            text = { Text(stringResource(R.string.paivaohjelman_suoritus_paattyy)) },
            confirmButton = {
                TextButton(onClick = {
                    run.end()
                    showExitConfirmation = false
                    view = ProgramsView.LIST
                }) { Text(stringResource(R.string.lopeta)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (timeSettingsOpen) {
        AlertDialog(
            onDismissRequest = { timeSettingsOpen = false },
            title = { Text(stringResource(R.string.ajan_esitystapa)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeModeChoices(timeMode) { mode ->
                        timeMode = mode
                        TimeDisplayPrefs.save(context, mode)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { timeSettingsOpen = false }) { Text(stringResource(R.string.dialog_done)) }
            }
        )
    }

    if (showRunSettings) {
        RunSettingsDialog(
            currentNextMode = nextPreviewMode,
            onNextModeChange = { nextPreviewMode = it },
            onDismiss = { showRunSettings = false }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.poistetaanko_paivaohjelma)) },
            text = {
                val usedInWeek = remember(target.id) {
                    WeekStore(context.applicationContext).load().events.any { it.programId == target.id }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.name)
                    if (usedInWeek) Text(stringResource(R.string.week_program_delete_note))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(target.id)
                        programs = store.load()
                        // Viikko-ohjelman tapahtumat, jotka käyttivät ohjelmaa, poistuvat samalla.
                        val week = WeekStore(context.applicationContext)
                        week.save(WeekSchedule.withoutMissingPrograms(week.load(), programs.map { it.id }.toSet()))
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Jäljellä oleva aika muodossa m:ss. */
private fun formatLeft(ms: Long): String {
    val seconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Päiväohjelma tämän päivän vaihemuutoksin (perutut ohitetaan, korvatut vaihdetaan). */
internal fun withTodayChanges(context: android.content.Context, program: DayProgram): DayProgram {
    val app = context.applicationContext
    val today = java.time.LocalDate.now()
    val changes = fi.aikapolku.app.data.DayChanges.stepChangesFor(
        fi.aikapolku.app.data.WeekStore(app).load(),
        fi.aikapolku.app.data.ChangeStore(app).load(),
        today,
        program.id
    )
    val texts: (Int) -> String = { app.getString(it) }
    return fi.aikapolku.app.data.DayChanges.applyToProgram(program, changes) { name, photo -> SubstepDefaults.forCard(name, texts, photo) }
}

/**
 * Kesken suorituksen tehty vaihemuutos tallennetaan tämän päivän tapahtumaan, jotta se säilyy,
 * jos ohjelma keskeytyy. Käyttäjä näki muutoksen jo suorituksessa, joten se kuitataan nähdyksi.
 * [card] null = vaihe ohitetaan tänään.
 */
internal fun saveRunStepChange(
    context: android.content.Context,
    program: DayProgram,
    step: fi.aikapolku.app.data.ProgramStep,
    runEventId: String?,
    card: fi.aikapolku.app.data.CardItem?
) {
    val app = context.applicationContext
    val today = java.time.LocalDate.now()
    val plan = fi.aikapolku.app.data.WeekStore(app).load()
    val todays = fi.aikapolku.app.data.WeekSchedule.eventsFor(plan, today)
        .filter { it.kind == fi.aikapolku.app.data.EventKind.PROGRAM && it.programId == program.id }
    val eventId = todays.firstOrNull { it.id == runEventId }?.id ?: todays.firstOrNull()?.id ?: return
    val saved = ProgramStore(app).load().firstOrNull { it.id == program.id }?.steps?.firstOrNull { it.id == step.id }
    val store = fi.aikapolku.app.data.ChangeStore(app)
    val change = fi.aikapolku.app.data.EventChange(
        id = java.util.UUID.randomUUID().toString(),
        date = today,
        eventId = eventId,
        type = if (card == null) fi.aikapolku.app.data.ChangeType.CANCEL else fi.aikapolku.app.data.ChangeType.REPLACE,
        cardName = card?.name ?: "",
        cardPhoto = card?.photoPath ?: "",
        reveal = fi.aikapolku.app.data.ChangeReveal.NOW,
        showOriginal = true,
        stepId = step.id,
        stepName = saved?.name ?: step.name,
        stepPhoto = saved?.photo ?: step.photo
    )
    store.upsert(change)
    fi.aikapolku.app.data.AckStore(app).ack(today, change.ackId)
}
