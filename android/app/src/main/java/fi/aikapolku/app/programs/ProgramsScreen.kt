package fi.aikapolku.app.programs

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.parseDraft
import fi.aikapolku.app.data.serializeDraft
import fi.aikapolku.app.programs.editor.ProgramEditorScreen
import fi.aikapolku.app.programs.run.RunNext
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.RunTime
import fi.aikapolku.app.programs.run.SupportMessageScreen
import fi.aikapolku.app.timer.BackgroundTimer
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

@Composable
internal fun ProgramsScreen() {
    val context = LocalContext.current
    val store = remember(context) {
        ProgramStore(context.applicationContext)
    }
    val completionSoundScope = rememberCoroutineScope()
    val p018Prefs = remember(context) {
        context.getSharedPreferences("aikapolku_program_progress_v1", Context.MODE_PRIVATE)
    }
    val storedProgramId = remember { p018Prefs.getString("program_id", "") ?: "" }
    val initialPrograms = remember(store) { store.load() }
    var programs by remember { mutableStateOf(initialPrograms) }
    var view by rememberSaveable {
        mutableStateOf(if (storedProgramId.isNotBlank()) "run" else "list")
    }

    var draft by rememberSaveable(stateSaver = programDraftSaver) {
        mutableStateOf(
            initialPrograms.firstOrNull { it.id == storedProgramId }
                ?: DayProgram(UUID.randomUUID().toString(), "", emptyList())
        )
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

    var stepIndex by rememberSaveable { mutableIntStateOf(p018Prefs.getInt("step_index", 0)) }
    var subStepIndex by rememberSaveable { mutableIntStateOf(p018Prefs.getInt("sub_step_index", 0)) }
    var supportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Oliko ajastin käynnissä, kun apua tai taukoa pyydettiin? Jatka palauttaa sen.
    var resumeAfterSupport by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(BackgroundTimer.running(context, "program")) }
    var stepVoicePlayed by rememberSaveable { mutableStateOf(false) }
    var deadline by rememberSaveable {
        mutableLongStateOf(SystemClock.elapsedRealtime() + BackgroundTimer.remaining(context, "program", 0L))
    }
    var remaining by rememberSaveable {
        mutableLongStateOf(BackgroundTimer.remaining(context, "program", 0L))
    }

    fun startProgram(program: DayProgram) {
        if (program.steps.isEmpty()) return
        draft = program
        showFullProgram = false
        stepIndex = 0
        subStepIndex = 0
        supportMessage = null
        running = false
        BackgroundTimer.stop(context, "program", program.steps.first().minutes * 60_000L)
        deadline = 0L
        remaining = program.steps.first().minutes * 60_000L
        p018Prefs.edit().putString("program_id", program.id)
            .putInt("step_index", 0).putInt("sub_step_index", 0).apply()
        view = "run"
        stepVoicePlayed = program.steps.first().mode != "duration"
        if (stepVoicePlayed && program.steps.first().autoPlayVoice) {
            VoicePlayer.play(context, program.steps.first().voicePath)
        }
    }

    fun advance() {
        running = false
        BackgroundTimer.stop(context, "program", 0L)
        if (stepIndex + 1 >= draft.steps.size) {
            p018Prefs.edit().remove("program_id").remove("step_index")
                .remove("sub_step_index").apply()
            view = "done"
        } else {
            stepIndex += 1
            subStepIndex = 0
            p018Prefs.edit().putInt("step_index", stepIndex)
                .putInt("sub_step_index", 0).apply()
            remaining = draft.steps[stepIndex].minutes * 60_000L
            deadline = 0L
            val newStep = draft.steps[stepIndex]
            stepVoicePlayed = newStep.mode != "duration"
            if (stepVoicePlayed && newStep.autoPlayVoice) {
                VoicePlayer.play(context, newStep.voicePath)
            }
        }
    }

    fun nextSubstepOrActivity() {
        val step = draft.steps.getOrNull(stepIndex)
        val count = step?.subSteps?.size ?: 0
        if (count > 0 && subStepIndex < count - 1) {
            subStepIndex += 1
            p018Prefs.edit().putInt("sub_step_index", subStepIndex).apply()
        } else {
            advance()
        }
    }

    fun pauseStepTimer() {
        if (running) {
            remaining = BackgroundTimer.remaining(context, "program", remaining)
            BackgroundTimer.stop(context, "program", remaining)
            running = false
        }
    }

    fun startStepTimer(step: ProgramStep) {
        if (!BackgroundTimer.allowed(context)) {
            BackgroundTimer.requestPermissions(context)
            return
        }
        if (remaining <= 0L) remaining = step.minutes * 60_000L
        deadline = BackgroundTimer.start(context, "program", remaining, step.name)
        running = true
        if (!stepVoicePlayed) {
            stepVoicePlayed = true
            if (step.autoPlayVoice) VoicePlayer.play(context, step.voicePath)
        }
    }

    // Aloita ja Jatka ovat aina vapaita; Tauko lukittuna vain PIN-koodilla.
    fun toggleStepTimer(step: ProgramStep) {
        when {
            running && caregiver.isLocked ->
                caregiver.run(AppAction.PAUSE_TIMER) { pauseStepTimer() }
            running -> pauseStepTimer()
            else -> startStepTimer(step)
        }
    }

    // Tarvitsen apua / tauon: aina vapaa, pysäyttää ajan ja näyttää viestin.
    fun requestSupport(message: String) {
        resumeAfterSupport = running
        pauseStepTimer()
        supportMessage = message
    }

    LaunchedEffect(view, running, deadline, stepIndex) {
        if (view == "run" && running) {
            while (true) {
                val left = (
                    deadline - SystemClock.elapsedRealtime()
                ).coerceAtLeast(0L)

                remaining = left

                if (left == 0L) {
                    running = false
                    if (BackgroundTimer.complete(context, "program")) {
                        val mode = BackgroundTimer.selectedSound(context)
                        if (mode != "off") completionSoundScope.launch { playCompletionSound(mode) }
                    }
                    break
                }

                delay(150L)
            }
        }
    }

    // No scrolling through old tasks after the user completes an action.
    // Snap immediately rather than animating, to avoid unnecessary motion.
    LaunchedEffect(view, stepIndex, subStepIndex) {
        programmeScroll.scrollTo(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val runStep = if (view == "run") draft.steps.getOrNull(stepIndex) else null
        if (runStep != null) {
            val message = supportMessage
            if (message != null) {
                SupportMessageScreen(
                    message = message,
                    timerPaused = resumeAfterSupport,
                    onContinue = {
                        supportMessage = null
                        if (resumeAfterSupport && remaining > 0L) startStepTimer(runStep)
                        resumeAfterSupport = false
                    },
                    onClose = {
                        supportMessage = null
                        resumeAfterSupport = false
                    }
                )
            } else {
                val nextSubStep = runStep.subSteps.getOrNull(subStepIndex + 1)
                val nextTask = draft.steps.getOrNull(stepIndex + 1)
                RunStepScreen(
                    taskName = runStep.name,
                    taskPhoto = runStep.photo,
                    description = runStep.description,
                    subSteps = runStep.subSteps,
                    subStepIndex = subStepIndex,
                    taskIndex = stepIndex,
                    taskCount = draft.steps.size,
                    time = when (runStep.mode) {
                        "duration" -> RunTime.Countdown(
                            remainingMs = remaining,
                            durationMs = maxOf(runStep.minutes * 60_000L, remaining),
                            running = running,
                            displayMode = timeMode,
                            pauseLocked = running && caregiver.isLocked
                        )
                        "clock" -> RunTime.Clock(runStep.clock)
                        else -> null
                    },
                    // Esikatselu: ensin seuraava osavaihe, viimeisessä osavaiheessa seuraava tehtävä.
                    next = when {
                        nextSubStep != null -> RunNext(nextSubStep, runStep.name, runStep.photo, isSubStep = true)
                        nextTask != null -> RunNext(nextTask.name, nextTask.name, nextTask.photo, isSubStep = false)
                        else -> null
                    },
                    nextMode = nextPreviewMode,
                    onToggleTimer = { toggleStepTimer(runStep) },
                    onHelp = { requestSupport("Tarvitsen apua") },
                    onBreak = { requestSupport("Tarvitsen tauon") },
                    onDone = { nextSubstepOrActivity() },
                    onSkip = { nextSubstepOrActivity() },
                    onMore = { showRunActions = true }
                )
            }
        } else if (view == "edit") {
            // P0-23: nelivaiheinen muokkain (ProgramEditor.kt) omana näkymänään.
            ProgramEditorScreen(
                draft = draft,
                onDraftChange = { draft = it },
                otherProgramNames = programs.filter { it.id != draft.id }.map { it.name },
                onSave = { edited ->
                    store.save(ProgramTiming.apply(edited).copy(name = edited.name.trim()))
                    programs = store.load()
                    view = "list"
                    // Kerran: "Käyttääkö tätä laitetta myös joku muu?"
                    caregiver.offerLockOnce()
                },
                onCancel = { view = "list" }
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
        "list" -> ProgramListView(
            programs = programs,
            canEdit = canEditPrograms,
            onNew = {
                draft = DayProgram(UUID.randomUUID().toString(), "", emptyList(), timeMode = ProgramTiming.FREE)
                view = "edit"
            },
            onTemplate = { template ->
                draft = ProgramTiming.infer(RoutineTemplates.create(template))
                view = "edit"
            },
            onStart = { startProgram(it) },
            onPrint = { ProgramPrint.open(context, it) },
            onEdit = {
                draft = ProgramTiming.infer(it)
                view = "edit"
            },
            onDelete = { deleteTarget = it }
        )

        "edit" -> Unit // Muokkain piirretään yllä (ProgramEditorScreen).

        "run" -> Unit // Suoritus piirretään yllä omana näkymänään (RunStepScreen).

        "done" -> ProgramDoneView(draft.name, onBack = { view = "list" })
    }
        } // Scrollable Column

        } // Ei suoritusta
    } // Content Box

    if (showRunActions && view == "run") {
        val current = draft.steps.getOrNull(stepIndex)
        RunActionsDialog(
            canExtend = current?.mode == "duration" && caregiver.allows(AppAction.EXTEND_TIME),
            canChangeSettings = caregiver.allows(AppAction.SETTINGS),
            canEnd = caregiver.allows(AppAction.END_PROGRAM),
            onExtend = {
                val left = if (running) BackgroundTimer.remaining(context, "program", remaining) else remaining
                val extended = left + 5 * 60_000L
                if (running) {
                    deadline = BackgroundTimer.start(context, "program", extended, current?.name ?: "Ajastin")
                } else {
                    BackgroundTimer.stop(context, "program", extended)
                }
                remaining = extended
                showRunActions = false
            },
            onTimeSettings = { showRunActions = false; timeSettingsOpen = true },
            onViewSettings = { showRunActions = false; showRunSettings = true },
            onShowProgram = { showRunActions = false; showFullProgram = true },
            onEnd = { showRunActions = false; showExitConfirmation = true },
            onDismiss = { showRunActions = false }
        )
    }

    if (showFullProgram && view == "run") {
        AlertDialog(
            onDismissRequest = { showFullProgram = false },
            title = { Text("Koko päiväohjelma") },
            text = { ProgramOverview(draft.steps, stepIndex) },
            confirmButton = {
                TextButton(onClick = { showFullProgram = false }) { Text("Sulje") }
            }
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Lopetetaanko päiväohjelma?") },
            text = { Text("Päiväohjelman suoritus päättyy.") },
            confirmButton = {
                TextButton(onClick = {
                    running = false
                    BackgroundTimer.stop(context, "program", remaining)
                    p018Prefs.edit().remove("program_id").remove("step_index")
                        .remove("sub_step_index").apply()
                    supportMessage = null
                    showExitConfirmation = false
                    view = "list"
                }) { Text("Lopeta") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Peruuta") }
            }
        )
    }

    if (timeSettingsOpen) {
        AlertDialog(
            onDismissRequest = { timeSettingsOpen = false },
            title = { Text("Ajan esitystapa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeModeChoices(timeMode) { mode ->
                        timeMode = mode
                        TimeDisplayPrefs.save(context, mode)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { timeSettingsOpen = false }) { Text("Valmis") }
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
            title = { Text("Poistetaanko päiväohjelma?") },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(target.id)
                        programs = store.load()
                        deleteTarget = null
                    }
                ) {
                    Text("Poista")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Peruuta")
                }
            }
        )
    }
}
