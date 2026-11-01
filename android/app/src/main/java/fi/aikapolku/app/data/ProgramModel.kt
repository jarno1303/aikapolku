package fi.aikapolku.app.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Toiminnon ajan esitys suorituksessa. [key] on tallennusmuodon arvo – älä muuta. */
internal enum class StepMode(val key: String) {
    /** Ei aikaa: Valmis siirtää seuraavaan. */
    MANUAL("manual"),
    /** Oma ajastin (ProgramStep.minutes). */
    DURATION("duration"),
    /** Kellonaika (ProgramStep.clock). */
    CLOCK("clock");

    companion object {
        fun fromKey(key: String?): StepMode = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/** Koko ohjelman aikatapa (P0-23). [key] on tallennusmuodon arvo – älä muuta. */
internal enum class TimeMode(val key: String) {
    FREE("free"),
    DURATION("duration"),
    CLOCK("clock");

    companion object {
        /** null = vanha ohjelma ilman aikatapaa; päätellään (ProgramTiming.infer). */
        fun fromKey(key: String?): TimeMode? = entries.firstOrNull { it.key == key }
    }
}

internal data class ProgramStep(
    val id: String,
    val name: String,
    val photo: String,
    val description: String,
    val mode: StepMode = StepMode.MANUAL,
    val minutes: Int = 5,
    val clock: String = "08:00",
    val voicePath: String = "",
    val autoPlayVoice: Boolean = false,
    val subSteps: List<String> = emptyList(),
    /** P0-23: kellonaikatilassa toiminto on lukittu kellonaikaan [clock]. */
    val fixedClock: Boolean = false
)

internal data class DayProgram(
    val id: String,
    val name: String,
    val steps: List<ProgramStep>,
    /** P0-23: koko ohjelman aikatapa; null = vanha ohjelma, päätellään. */
    val timeMode: TimeMode? = null,
    val startClock: String = ProgramTiming.DEFAULT_START
)

internal fun stepToJson(step: ProgramStep): JSONObject = JSONObject().apply {
    put("id", step.id)
    put("name", step.name)
    put("photo", step.photo)
    put("description", step.description)
    put("mode", step.mode.key)
    put("minutes", step.minutes)
    put("clock", step.clock)
    put("voicePath", step.voicePath)
    put("autoPlayVoice", step.autoPlayVoice)
    put("subSteps", writeSubSteps(step.subSteps))
    put("fixedClock", step.fixedClock)
}

internal fun stepFromJson(s: JSONObject): ProgramStep = ProgramStep(
    id = s.optString("id"),
    name = s.optString("name"),
    photo = s.optString("photo"),
    description = s.optString("description"),
    mode = StepMode.fromKey(s.optString("mode", StepMode.MANUAL.key)),
    minutes = s.optInt("minutes", 5),
    clock = s.optString("clock", "08:00"),
    voicePath = s.optString("voicePath"),
    autoPlayVoice = s.optBoolean("autoPlayVoice", false),
    subSteps = readSubSteps(s),
    fixedClock = s.optBoolean("fixedClock", false)
)

internal fun programToJson(program: DayProgram): JSONObject = JSONObject().apply {
    put("id", program.id)
    put("name", program.name)
    put("steps", JSONArray().also { arr -> program.steps.forEach { arr.put(stepToJson(it)) } })
    put("timeMode", program.timeMode?.key ?: "")
    put("startClock", program.startClock)
}

internal fun programFromJson(obj: JSONObject): DayProgram {
    val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
    val steps = (0 until stepsArray.length()).mapNotNull { j ->
        stepsArray.optJSONObject(j)?.let(::stepFromJson)
    }
    return DayProgram(
        id = obj.optString("id"),
        name = obj.optString("name"),
        steps = steps,
        timeMode = TimeMode.fromKey(obj.optString("timeMode", "")),
        startClock = obj.optString("startClock", ProgramTiming.DEFAULT_START)
    )
}

/** Old stored programmes remain readable because missing subSteps means no substeps. */
private fun readSubSteps(obj: JSONObject): List<String> {
    val source = obj.optJSONArray("subSteps") ?: return emptyList()
    return (0 until minOf(source.length(), 16)).mapNotNull { index ->
        source.optString(index).trim().take(72).takeIf { it.isNotEmpty() }
    }
}

private fun writeSubSteps(items: List<String>): JSONArray = JSONArray().also { result ->
    items.take(16).forEach { item ->
        item.trim().take(72).takeIf { it.isNotEmpty() }?.let { result.put(it) }
    }
}

internal fun serializeDraft(program: DayProgram): String =
    programToJson(program).toString()

internal fun parseDraft(encoded: String): DayProgram =
    runCatching { programFromJson(JSONObject(encoded)) }.getOrElse {
        DayProgram(UUID.randomUUID().toString(), "", emptyList())
    }
