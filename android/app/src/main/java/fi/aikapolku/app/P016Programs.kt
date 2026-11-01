package fi.aikapolku.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val ap16Teal = androidx.compose.ui.graphics.Color(0xFF0F766E)

internal data class AP16Step(
    val id: String,
    val name: String,
    val photo: String,
    val description: String,
    val mode: String = "manual",
    val minutes: Int = 5,
    val clock: String = "08:00",
    val voicePath: String = "",
    val autoPlayVoice: Boolean = false,
    val subSteps: List<String> = emptyList(),
    /** P0-23: kellonaikatilassa toiminto on lukittu kellonaikaan [clock]. */
    val fixedClock: Boolean = false
)

internal data class AP16Program(
    val id: String,
    val name: String,
    val steps: List<AP16Step>,
    /** P0-23: ProgramTiming.FREE / DURATION / CLOCK. Tyhjä = vanha ohjelma, päätellään. */
    val timeMode: String = "",
    val startClock: String = ProgramTiming.DEFAULT_START
)

internal fun ap16StepToJson(step: AP16Step): JSONObject = JSONObject().apply {
    put("id", step.id)
    put("name", step.name)
    put("photo", step.photo)
    put("description", step.description)
    put("mode", step.mode)
    put("minutes", step.minutes)
    put("clock", step.clock)
    put("voicePath", step.voicePath)
    put("autoPlayVoice", step.autoPlayVoice)
    put("subSteps", p021WriteSubSteps(step.subSteps))
    put("fixedClock", step.fixedClock)
}

internal fun ap16StepFromJson(s: JSONObject): AP16Step = AP16Step(
    id = s.optString("id"),
    name = s.optString("name"),
    photo = s.optString("photo"),
    description = s.optString("description"),
    mode = s.optString("mode", "manual"),
    minutes = s.optInt("minutes", 5),
    clock = s.optString("clock", "08:00"),
    voicePath = s.optString("voicePath"),
    autoPlayVoice = s.optBoolean("autoPlayVoice", false),
    subSteps = p021ReadSubSteps(s),
    fixedClock = s.optBoolean("fixedClock", false)
)

internal fun ap16ProgramToJson(program: AP16Program): JSONObject = JSONObject().apply {
    put("id", program.id)
    put("name", program.name)
    put("steps", JSONArray().also { arr -> program.steps.forEach { arr.put(ap16StepToJson(it)) } })
    put("timeMode", program.timeMode)
    put("startClock", program.startClock)
}

internal fun ap16ProgramFromJson(obj: JSONObject): AP16Program {
    val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
    val steps = (0 until stepsArray.length()).mapNotNull { j ->
        stepsArray.optJSONObject(j)?.let(::ap16StepFromJson)
    }
    return AP16Program(
        id = obj.optString("id"),
        name = obj.optString("name"),
        steps = steps,
        timeMode = obj.optString("timeMode", ""),
        startClock = obj.optString("startClock", ProgramTiming.DEFAULT_START)
    )
}

internal class AP16Store(context: Context) {
    private val prefs = context.getSharedPreferences(
        "aikapolku_programs_v1",
        Context.MODE_PRIVATE
    )

    fun load(): List<AP16Program> {
        val array = runCatching {
            JSONArray(prefs.getString("programs", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::ap16ProgramFromJson)
        }
    }

    private fun write(programs: List<AP16Program>) {
        val array = JSONArray()
        programs.forEach { array.put(ap16ProgramToJson(it)) }

        prefs.edit()
            .putString("programs", array.toString())
            .apply()
    }

    fun save(program: AP16Program) {
        val programs = load().toMutableList()
        val index = programs.indexOfFirst { it.id == program.id }

        if (index >= 0) {
            programs[index] = program
        } else {
            programs.add(program)
        }

        write(programs)
    }

    fun delete(id: String) {
        write(load().filterNot { it.id == id })
    }
}

/** Old stored programmes remain readable because missing subSteps means no substeps. */
private fun p021ReadSubSteps(obj: JSONObject): List<String> {
    val source = obj.optJSONArray("subSteps") ?: return emptyList()
    return (0 until minOf(source.length(), 16)).mapNotNull { index ->
        source.optString(index).trim().take(72).takeIf { it.isNotEmpty() }
    }
}

private fun p021WriteSubSteps(items: List<String>): JSONArray = JSONArray().also { result ->
    items.take(16).forEach { item ->
        item.trim().take(72).takeIf { it.isNotEmpty() }?.let { result.put(it) }
    }
}

// The program and its monotonic countdown deadline survive configuration changes.
private val ap17ProgramSaver = Saver<AP16Program, String>(
    save = { p017SerializeDraft(it) },
    restore = { p017ParseDraft(it) }
)

internal fun p017SerializeDraft(program: AP16Program): String =
    ap16ProgramToJson(program).toString()

internal fun p017ParseDraft(encoded: String): AP16Program =
    runCatching { ap16ProgramFromJson(JSONObject(encoded)) }.getOrElse {
        AP16Program(UUID.randomUUID().toString(), "", emptyList())
    }

@Composable
internal fun P016ProgramsScreen() {
    val context = LocalContext.current
    val store = remember(context) {
        AP16Store(context.applicationContext)
    }
    val completionSoundScope = rememberCoroutineScope()
    val p018Prefs = remember(context) {
        context.getSharedPreferences("aikapolku_program_progress_v1", Context.MODE_PRIVATE)
    }
    val storedProgramId = remember { p018Prefs.getString("program_id", "") ?: "" }
    var programs by remember { mutableStateOf(store.load()) }
    var view by rememberSaveable {
        mutableStateOf(if (storedProgramId.isNotBlank()) "run" else "list")
    }

    var draft by rememberSaveable(stateSaver = ap17ProgramSaver) {
        mutableStateOf(
            store.load().firstOrNull { it.id == storedProgramId }
                ?: AP16Program(UUID.randomUUID().toString(), "", emptyList())
        )
    }

    var showFullProgram by rememberSaveable { mutableStateOf(false) }
    var showRunActions by remember { mutableStateOf(false) }

    // P0-21e: ohjaajan lukitus. Käynnistys, tulostus, apu, tauko, Valmis ja ohitus ovat aina vapaita.
    val caregiver = LocalCaregiver.current
    val canEditPrograms = caregiver.allows(AppAction.EDIT_PROGRAMS)
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showP021cSettings by remember { mutableStateOf(false) }
    var nextPreviewMode by rememberSaveable {
        mutableStateOf(P021cPreferences.nextMode(context))
    }
    val programmeScroll = rememberScrollState()
    var deleteTarget by remember { mutableStateOf<AP16Program?>(null) }
    var timeSettingsOpen by remember { mutableStateOf(false) }

    // Jos lukitus menee päälle, ohjaajan ikkunat suljetaan. Muokkausnäkymä jätetään
    // tarkoituksella auki, jotta tallentamattomat muutokset eivät katoa.
    LaunchedEffect(caregiver.isLocked) {
        if (caregiver.isLocked) {
            timeSettingsOpen = false
            showP021cSettings = false
            showExitConfirmation = false
            deleteTarget = null
        }
    }
    var timeMode by rememberSaveable {
        mutableStateOf(P020TimeDisplay.load(context))
    }

    var stepIndex by rememberSaveable { mutableIntStateOf(p018Prefs.getInt("step_index", 0)) }
    var subStepIndex by rememberSaveable { mutableIntStateOf(p018Prefs.getInt("sub_step_index", 0)) }
    var supportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Oliko ajastin käynnissä, kun apua tai taukoa pyydettiin? Jatka palauttaa sen.
    var resumeAfterSupport by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(P018Timer.running(context, "program")) }
    var stepVoicePlayed by rememberSaveable { mutableStateOf(false) }
    var deadline by rememberSaveable {
        mutableLongStateOf(SystemClock.elapsedRealtime() + P018Timer.remaining(context, "program", 0L))
    }
    var remaining by rememberSaveable {
        mutableLongStateOf(P018Timer.remaining(context, "program", 0L))
    }

    fun startProgram(program: AP16Program) {
        if (program.steps.isEmpty()) return
        draft = program
        showFullProgram = false
        stepIndex = 0
        subStepIndex = 0
        supportMessage = null
        running = false
        P018Timer.stop(context, "program", program.steps.first().minutes * 60_000L)
        deadline = 0L
        remaining = program.steps.first().minutes * 60_000L
        p018Prefs.edit().putString("program_id", program.id)
            .putInt("step_index", 0).putInt("sub_step_index", 0).apply()
        view = "run"
        stepVoicePlayed = program.steps.first().mode != "duration"
        if (stepVoicePlayed && program.steps.first().autoPlayVoice) {
            P017VoicePlayer.play(context, program.steps.first().voicePath)
        }
    }

    fun advance() {
        running = false
        P018Timer.stop(context, "program", 0L)
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
                P017VoicePlayer.play(context, newStep.voicePath)
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
            remaining = P018Timer.remaining(context, "program", remaining)
            P018Timer.stop(context, "program", remaining)
            running = false
        }
    }

    fun startStepTimer(step: AP16Step) {
        if (!P018Timer.allowed(context)) {
            P018Timer.requestPermissions(context)
            return
        }
        if (remaining <= 0L) remaining = step.minutes * 60_000L
        deadline = P018Timer.start(context, "program", remaining, step.name)
        running = true
        if (!stepVoicePlayed) {
            stepVoicePlayed = true
            if (step.autoPlayVoice) P017VoicePlayer.play(context, step.voicePath)
        }
    }

    // Aloita ja Jatka ovat aina vapaita; Tauko lukittuna vain PIN-koodilla.
    fun toggleStepTimer(step: AP16Step) {
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
                    if (P018Timer.complete(context, "program")) {
                        val mode = P018Timer.selectedSound(context)
                        if (mode != "off") completionSoundScope.launch { p016PlaySound(mode) }
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
        "list" -> {
            Text(
                "Päiväohjelmat",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (!canEditPrograms) {
                CaregiverLockButton(label = "Ohjaaja: luo ja muokkaa ohjelmia")
            }

            if (canEditPrograms) {
            Button(
                onClick = {
                    draft = AP16Program(
                        UUID.randomUUID().toString(),
                        "",
                        emptyList(),
                        timeMode = ProgramTiming.FREE
                    )
                    view = "edit"
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text("+ Uusi päiväohjelma")
            }

            Text("Aloita valmiista rutiinista", fontWeight = FontWeight.SemiBold)
            Text(
                "Pohjaa voi muokata ennen tallentamista.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                P020RoutineTemplates.all.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { template ->
                            OutlinedButton(
                                onClick = {
                                    draft = ProgramTiming.infer(P020RoutineTemplates.create(template))
                                    view = "edit"
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                            ) {
                                Text(template.name, fontSize = 13.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            } // canEditPrograms

            if (programs.isEmpty()) {
                Text(
                    "Ei vielä päiväohjelmia. Luo ensimmäinen ohjelma.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            programs.forEach { program ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            program.name,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${program.steps.size} toimintoa")

                        OutlinedButton(
                            onClick = {
                                P019cPrint.open(context, program)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text("Tulosta / PDF")
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { startProgram(program) }
                            ) {
                                Text("Käynnistä")
                            }
                            if (canEditPrograms) {
                                TextButton(
                                    onClick = {
                                        draft = ProgramTiming.infer(program)
                                        view = "edit"
                                    }
                                ) {
                                    Text("Muokkaa")
                                }
                                TextButton(
                                    onClick = { deleteTarget = program }
                                ) {
                                    Text("Poista")
                                }
                            }
                        }
                    }
                }
            }
        }

        "edit" -> Unit // Muokkain piirretään yllä (ProgramEditorScreen).

        "run" -> Unit // Suoritus piirretään yllä omana näkymänään (RunStepScreen).

        "done" -> {
            Spacer(Modifier.height(30.dp))

            Text(
                "✓ Kaikki valmista!",
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold
            )

            Text("${draft.name} on päättynyt.")

            Button(
                onClick = { view = "list" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text("Takaisin päiväohjelmiin")
            }
        }
    }
        } // Scrollable Column

        } // Ei suoritusta
    } // Content Box

    if (showRunActions && view == "run") {
        val current = draft.steps.getOrNull(stepIndex)
        AlertDialog(
            onDismissRequest = { showRunActions = false },
            title = { Text("Muut toiminnot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (current?.mode == "duration" && caregiver.allows(AppAction.EXTEND_TIME)) {
                        TextButton(onClick = {
                            val left = if (running)
                                P018Timer.remaining(context, "program", remaining)
                            else remaining
                            val extended = left + 5 * 60_000L
                            if (running) {
                                deadline = P018Timer.start(context, "program", extended, current?.name ?: "Ajastin")
                            } else {
                                P018Timer.stop(context, "program", extended)
                            }
                            remaining = extended
                            showRunActions = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("+ 5 min lisäaikaa") }
                    }
                    if (caregiver.allows(AppAction.SETTINGS)) {
                        TextButton(onClick = {
                            showRunActions = false
                            timeSettingsOpen = true
                        }, modifier = Modifier.fillMaxWidth()) { Text("Ajan näyttötapa") }
                        TextButton(onClick = {
                            showRunActions = false
                            showP021cSettings = true
                        }, modifier = Modifier.fillMaxWidth()) { Text("Näkymä ja viestiäänet") }
                    }
                    TextButton(onClick = {
                        showRunActions = false
                        showFullProgram = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("Näytä koko ohjelma") }
                    HorizontalDivider()
                    if (caregiver.allows(AppAction.END_PROGRAM)) {
                        TextButton(onClick = {
                            showRunActions = false
                            showExitConfirmation = true
                        }, modifier = Modifier.fillMaxWidth()) { Text("Lopeta ohjelma") }
                    } else {
                        CaregiverLockButton(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Ohjaajan toiminnot"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRunActions = false }) { Text("Sulje") }
            }
        )
    }

    if (showFullProgram && view == "run") {
        AlertDialog(
            onDismissRequest = { showFullProgram = false },
            title = { Text("Koko päiväohjelma") },
            text = { P019ProgramOverview(draft.steps, stepIndex) },
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
                    P018Timer.stop(context, "program", remaining)
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
                    P020TimeModeChoices(timeMode) { mode ->
                        timeMode = mode
                        P020TimeDisplay.save(context, mode)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { timeSettingsOpen = false }) { Text("Valmis") }
            }
        )
    }

    if (showP021cSettings) {
        P021cSettingsDialog(
            currentNextMode = nextPreviewMode,
            onNextModeChange = { nextPreviewMode = it },
            onDismiss = { showP021cSettings = false }
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

@Composable
private fun P019ProgramOverview(
    steps: List<AP16Step>,
    currentIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { index, item ->
            val status = when {
                index < currentIndex -> "VALMIS"
                index == currentIndex -> "NYT"
                index == currentIndex + 1 -> "SEURAAVAKSI"
                else -> "TULOSSA"
            }

            val color = when {
                index == currentIndex ->
                    androidx.compose.ui.graphics.Color(0xFFE6F4F1)
                index < currentIndex ->
                    androidx.compose.ui.graphics.Color(0xFFF1F5F4)
                else ->
                    MaterialTheme.colorScheme.surfaceVariant
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = color
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    P011Photo(
                        item.name,
                        item.photo,
                        Modifier.size(44.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${index + 1}. ${item.name}",
                            fontWeight = if (index == currentIndex)
                                FontWeight.Bold
                            else FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            status,
                            color = if (index == currentIndex)
                                ap16Teal
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (item.mode == "clock") {
                        Text(
                            item.clock,
                            fontSize = 14.sp
                        )
                    } else if (item.mode == "duration") {
                        Text(
                            "${item.minutes} min",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
