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

@Composable
internal fun ProgramsScreen() {
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
    val resumable = remember { initialPrograms.firstOrNull { it.id == runProgress.programId && it.steps.isNotEmpty() } }
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
        run.start(program)
        view = ProgramsView.RUN
    }

    fun next() {
        if (run.next()) view = ProgramsView.DONE
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
                    taskIndex = run.stepIndex,
                    taskCount = run.program.steps.size,
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
                    onDone = { next() },
                    onSkip = { next() },
                    onMore = { showRunActions = true }
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
        ProgramsView.LIST -> ProgramListView(
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
            onEdit = {
                draft = ProgramTiming.infer(it)
                view = ProgramsView.EDIT
            },
            onDelete = { deleteTarget = it }
        )

        ProgramsView.EDIT -> Unit // Muokkain piirretään yllä (ProgramEditorScreen).

        ProgramsView.RUN -> Unit // Suoritus piirretään yllä omana näkymänään (RunStepScreen).

        ProgramsView.DONE -> ProgramDoneView(run.program.name, onBack = { view = ProgramsView.LIST })
    }
        } // Scrollable Column

        } // Ei suoritusta
    } // Content Box

    if (showRunActions && view == ProgramsView.RUN) {
        val current = run.currentStep
        val fallbackTitle = stringResource(R.string.ajastin)
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
            onEnd = { showRunActions = false; showExitConfirmation = true },
            onDismiss = { showRunActions = false }
        )
    }

    if (showFullProgram && view == ProgramsView.RUN) {
        AlertDialog(
            onDismissRequest = { showFullProgram = false },
            title = { Text(stringResource(R.string.koko_paivaohjelma)) },
            text = { ProgramOverview(run.program.steps, run.stepIndex) },
            confirmButton = {
                TextButton(onClick = { showFullProgram = false }) { Text(stringResource(R.string.sulje)) }
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
                TextButton(onClick = { showExitConfirmation = false }) { Text(stringResource(R.string.peruuta)) }
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
                TextButton(onClick = { timeSettingsOpen = false }) { Text(stringResource(R.string.valmis_4)) }
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
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(target.id)
                        programs = store.load()
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.poista))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.peruuta))
                }
            }
        )
    }
}
