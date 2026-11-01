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
    val subSteps: List<String> = emptyList()
)

internal data class AP16Program(
    val id: String,
    val name: String,
    val steps: List<AP16Step>
)

internal class AP16Store(context: Context) {
    private val prefs = context.getSharedPreferences(
        "aikapolku_programs_v1",
        Context.MODE_PRIVATE
    )

    fun load(): List<AP16Program> {
        val array = runCatching {
            JSONArray(prefs.getString("programs", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val stepsArray = obj.optJSONArray("steps") ?: JSONArray()

                val steps = buildList {
                    for (j in 0 until stepsArray.length()) {
                        val s = stepsArray.optJSONObject(j) ?: continue
                        add(
                            AP16Step(
                                id = s.optString("id"),
                                name = s.optString("name"),
                                photo = s.optString("photo"),
                                description = s.optString("description"),
                                mode = s.optString("mode", "manual"),
                                minutes = s.optInt("minutes", 5),
                                clock = s.optString("clock", "08:00"),
                                voicePath = s.optString("voicePath"),
                                autoPlayVoice = s.optBoolean("autoPlayVoice", false),
                                subSteps = p021ReadSubSteps(s)
                            )
                        )
                    }
                }

                add(
                    AP16Program(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        steps = steps
                    )
                )
            }
        }
    }

    private fun write(programs: List<AP16Program>) {
        val array = JSONArray()

        programs.forEach { program ->
            val steps = JSONArray()

            program.steps.forEach { step ->
                steps.put(
                    JSONObject().apply {
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
                    }
                )
            }

            array.put(
                JSONObject().apply {
                    put("id", program.id)
                    put("name", program.name)
                    put("steps", steps)
                }
            )
        }

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

internal fun p017SerializeDraft(program: AP16Program): String {
    val steps = JSONArray()
    program.steps.forEach { step ->
        steps.put(JSONObject().apply {
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
        })
    }
    return JSONObject().apply {
        put("id", program.id)
        put("name", program.name)
        put("steps", steps)
    }.toString()
}

internal fun p017ParseDraft(encoded: String): AP16Program {
    return runCatching {
        val obj = JSONObject(encoded)
        val jsonSteps = obj.optJSONArray("steps") ?: JSONArray()
        val steps = buildList {
            for (i in 0 until jsonSteps.length()) {
                val step = jsonSteps.optJSONObject(i) ?: continue
                add(AP16Step(
                    id = step.optString("id"),
                    name = step.optString("name"),
                    photo = step.optString("photo"),
                    description = step.optString("description"),
                    mode = step.optString("mode", "manual"),
                    minutes = step.optInt("minutes", 5),
                    clock = step.optString("clock", "08:00"),
                    voicePath = step.optString("voicePath"),
                    autoPlayVoice = step.optBoolean("autoPlayVoice", false),
                    subSteps = p021ReadSubSteps(step)
                ))
            }
        }
        AP16Program(obj.optString("id"), obj.optString("name"), steps)
    }.getOrElse {
        AP16Program(UUID.randomUUID().toString(), "", emptyList())
    }
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

    var addCard by remember { mutableStateOf(false) }
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

    fun requestSupport(message: String) {
        if (running) {
            remaining = P018Timer.remaining(context, "program", remaining)
            P018Timer.stop(context, "program", remaining)
            running = false
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(programmeScroll)
                .padding(bottom = if (view == "run") 166.dp else 12.dp),
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
                        emptyList()
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
                                    draft = P020RoutineTemplates.create(template)
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
                                        draft = program
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

        "edit" -> {
            Text(
                "Päiväohjelman muokkaus",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it.take(80))
                },
                label = { Text("Ohjelman nimi") },
                placeholder = { Text("Esim. Aamurutiini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            draft.steps.forEachIndexed { index, step ->
                P016StepEditor(
                    number = index + 1,
                    step = step,
                    canUp = index > 0,
                    canDown = index < draft.steps.lastIndex,
                    onUpdate = { updated ->
                        val list = draft.steps.toMutableList()
                        list[index] = updated
                        draft = draft.copy(steps = list)
                    },
                    onUp = {
                        val list = draft.steps.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index - 1, item)
                        draft = draft.copy(steps = list)
                    },
                    onDown = {
                        val list = draft.steps.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index + 1, item)
                        draft = draft.copy(steps = list)
                    },
                    onDelete = {
                        draft = draft.copy(
                            steps = draft.steps.filterNot {
                                it.id == step.id
                            }
                        )
                    }
                )
            }

            OutlinedButton(
                onClick = { addCard = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) {
                Text("+ Lisää toiminto")
            }

            val validTimes = draft.steps.all {
                it.mode != "clock" ||
                    Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
                        .matches(it.clock)
            }

            if (!validTimes) {
                Text(
                    "Kellonaika annetaan muodossa 08:30.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                enabled = draft.name.isNotBlank() &&
                    draft.steps.isNotEmpty() &&
                    validTimes,
                onClick = {
                    store.save(draft.copy(name = draft.name.trim()))
                    programs = store.load()
                    view = "list"
                    // Kerran: "Käyttääkö tätä laitetta myös joku muu?"
                    caregiver.offerLockOnce()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text("Tallenna päiväohjelma")
            }

            TextButton(
                onClick = { view = "list" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Peruuta")
            }
        }

        "run" -> {
            val step = draft.steps.getOrNull(stepIndex)
            if (step != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        draft.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        "${stepIndex + 1}/${draft.steps.size}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                P021FocusedActivityCard(
                    title = step.name,
                    photo = step.photo,
                    subSteps = step.subSteps,
                    currentIndex = subStepIndex
                )

                when (step.mode) {
                    "duration" -> {
                        val seconds = (remaining + 999L) / 1000L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (timeMode == P020TimeDisplay.NUMBERS) {
                                Text(
                                    "%02d:%02d".format(seconds / 60, seconds % 60),
                                    fontSize = 31.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                P020TimeVisual(
                                    remaining = remaining,
                                    duration = maxOf(step.minutes * 60_000L, remaining),
                                    mode = timeMode,
                                    running = running,
                                    modifier = Modifier.size(105.dp)
                                )
                            }

                            val pauseLocked = running && !caregiver.allows(AppAction.PAUSE_TIMER)
                            Button(
                                onClick = {
                                    if (pauseLocked) {
                                        // Tauko lukittuna vain PIN-koodilla. Tarvitsen tauon toimii aina.
                                        caregiver.run(AppAction.PAUSE_TIMER) {
                                            if (running) {
                                                remaining = P018Timer.remaining(context, "program", remaining)
                                                P018Timer.stop(context, "program", remaining)
                                                running = false
                                            }
                                        }
                                    } else if (running) {
                                        remaining = P018Timer.remaining(context, "program", remaining)
                                        P018Timer.stop(context, "program", remaining)
                                        running = false
                                    } else if (!P018Timer.allowed(context)) {
                                        P018Timer.requestPermissions(context)
                                    } else {
                                        if (remaining <= 0L) remaining = step.minutes * 60_000L
                                        deadline = P018Timer.start(context, "program", remaining, step.name)
                                        running = true
                                        if (!stepVoicePlayed) {
                                            stepVoicePlayed = true
                                            if (step.autoPlayVoice) {
                                                P017VoicePlayer.play(context, step.voicePath)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) {
                                Text(
                                    if (pauseLocked) "🔒 Tauko"
                                    else if (running) "Tauko"
                                    else if (remaining == 0L) "Aloita uudelleen"
                                    else if (remaining < step.minutes * 60_000L) "Jatka"
                                    else "Aloita"
                                )
                            }
                        }
                        if (remaining == 0L) {
                            Text("Aika päättyi", color = ap16Teal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    "clock" -> Text("Klo ${step.clock}", fontSize = 17.sp)
                    else -> Unit // No deadline: don't add an unnecessary text line.
                }

                // Preview only the next SUBSTEP until the last substep, then the next activity.
                // The preview never accepts clicks, and can be hidden for sensory comfort.
                val nextSubStep = step.subSteps.getOrNull(subStepIndex + 1)
                if (nextSubStep != null) {
                    P021cNextPreview(
                        name = nextSubStep,
                        photo = step.photo,
                        mode = nextPreviewMode,
                        isSubStep = true
                    )
                } else {
                    draft.steps.getOrNull(stepIndex + 1)?.let { nextStep ->
                        P021cNextPreview(
                            name = nextStep.name,
                            photo = nextStep.photo,
                            mode = nextPreviewMode,
                            isSubStep = false
                        )
                    }
                }
            }
        }

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

    // This surface stays visible even if the content above is long.
    if (view == "run" && draft.steps.getOrNull(stepIndex) != null) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 5.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                P021SupportButtons(
                    onHelp = { requestSupport("Tarvitsen apua") },
                    onBreak = { requestSupport("Tarvitsen tauon") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { nextSubstepOrActivity() },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) { Text("Valmis", fontSize = 18.sp) }
                    OutlinedButton(
                        onClick = { showRunActions = true },
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) { Text("Lisää") }
                }
            }
        }
    }
    } // Content Box

    if (showRunActions && view == "run") {
        val current = draft.steps.getOrNull(stepIndex)
        AlertDialog(
            onDismissRequest = { showRunActions = false },
            title = { Text("Muut toiminnot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        showRunActions = false
                        nextSubstepOrActivity()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (current?.subSteps?.isNotEmpty() == true) "Ohita osavaihe" else "Ohita tehtävä")
                    }
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

    supportMessage?.let { message ->
        P021SupportMessageDialog(
            message = message,
            onDismiss = { supportMessage = null }
        )
    }

    if (addCard) {
        val customCards = P015CardStore(context).loadCards()

        val defaults = AikaCardArt.templates.map { template ->
            P015Card(
                id = "basic_${template.id}",
                name = template.name,
                description = "",
                photoPath = "builtin:${template.id}"
            )
        }

        val choices = (customCards + defaults)
            .distinctBy { it.name.lowercase() }

        AlertDialog(
            onDismissRequest = { addCard = false },
            title = { Text("Lisää toiminto") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    choices.forEach { card ->
                        TextButton(
                            onClick = {
                                draft = draft.copy(
                                    steps = draft.steps + AP16Step(
                                        id = UUID.randomUUID().toString(),
                                        name = card.name,
                                        photo = card.photoPath,
                                        description = card.description,
                                        voicePath = card.voicePath,
                                        autoPlayVoice = card.autoPlayVoice,
                                        subSteps = P021SubstepDefaults.forCard(card.name)
                                    )
                                )
                                addCard = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                P011Photo(
                                    name = card.name,
                                    imagePath = card.photoPath,
                                    modifier = Modifier.size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Text(card.name, fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addCard = false }) {
                    Text("Sulje")
                }
            }
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
@Composable
private fun P016StepEditor(
    number: Int,
    step: AP16Step,
    canUp: Boolean,
    canDown: Boolean,
    onUpdate: (AP16Step) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit
) {
    var minutesMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                P011Photo(
                    step.name,
                    step.photo,
                    Modifier.size(60.dp)
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        "$number. ${step.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    if (step.description.isNotBlank()) {
                        Text(step.description, fontSize = 12.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onUp,
                    enabled = canUp
                ) { Text("↑") }

                OutlinedButton(
                    onClick = onDown,
                    enabled = canDown
                ) { Text("↓") }

                TextButton(onClick = onDelete) {
                    Text("Poista")
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "manual" to "Käsin",
                    "duration" to "Kesto",
                    "clock" to "Kellonaika"
                ).forEach { (value, title) ->
                    FilterChip(
                        selected = step.mode == value,
                        onClick = {
                            onUpdate(step.copy(mode = value))
                        },
                        label = { Text(title) }
                    )
                }
            }

            if (step.mode == "duration") {
                Box {
                    OutlinedButton(
                        onClick = { minutesMenu = true }
                    ) {
                        Text("Kesto: ${step.minutes} min")
                    }

                    DropdownMenu(
                        expanded = minutesMenu,
                        onDismissRequest = { minutesMenu = false }
                    ) {
                        listOf(1,2,5,10,15,20,30,45,60).forEach { min ->
                            DropdownMenuItem(
                                text = { Text("$min min") },
                                onClick = {
                                    onUpdate(step.copy(minutes = min))
                                    minutesMenu = false
                                }
                            )
                        }
                    }
                }
            }

            P021SubstepEditor(
                step = step,
                onUpdate = onUpdate
            )

            if (step.mode == "clock") {
                val timeContext = LocalContext.current

                OutlinedButton(
                    onClick = {
                        val hour = step.clock
                            .substringBefore(":")
                            .toIntOrNull()
                            ?.coerceIn(0, 23) ?: 8

                        val minute = step.clock
                            .substringAfter(":", "00")
                            .toIntOrNull()
                            ?.coerceIn(0, 59) ?: 0

                        android.app.TimePickerDialog(
                            timeContext,
                            { _, selectedHour, selectedMinute ->
                                val formatted = String.format(
                                    java.util.Locale.ROOT,
                                    "%02d:%02d",
                                    selectedHour,
                                    selectedMinute
                                )
                                onUpdate(step.copy(clock = formatted))
                            },
                            hour,
                            minute,
                            true
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Klo ${step.clock} – muuta")
                }

            }
        }
    }
}