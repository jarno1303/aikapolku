package fi.aikapolku.app.programs.run

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.parseDraft
import fi.aikapolku.app.data.serializeDraft
import fi.aikapolku.app.timer.Tick
import fi.aikapolku.app.timer.TimerPort

/** Missä kohtaa ohjelmaa ollaan; säilyy sovelluksen sulkemisen yli. */
internal interface RunProgress {
    val programId: String
    val stepIndex: Int
    val subStepIndex: Int
    fun save(programId: String, stepIndex: Int, subStepIndex: Int)
    fun clear()
}

/**
 * Päiväohjelman suorituksen tila ja säännöt: missä tehtävässä ja osavaiheessa ollaan, ajastin,
 * apu- ja taukopyynnöt. Ei riipu käyttöliittymästä, joten säännöt voi testata suoraan.
 *
 * Säännöt (P0-21/P0-22a):
 *  - Valmis ja Ohita vievät seuraavaan osavaiheeseen, viimeisestä seuraavaan tehtävään.
 *  - Kestotehtävän puheviesti soi, kun ajastin käynnistetään; muissa heti tehtävän alkaessa.
 *  - Apu- ja taukopyyntö pysäyttää ajan; Jatka käynnistää sen uudelleen, jos se oli käynnissä.
 */
internal class ProgramRunController(
    private val timer: TimerPort,
    private val progress: RunProgress,
    private val playVoice: (String) -> Unit
) {
    var program by mutableStateOf(DayProgram("", "", emptyList()))
        private set
    var stepIndex by mutableIntStateOf(0)
        private set
    var subStepIndex by mutableIntStateOf(0)
        private set
    /** Näkyvä apu- tai taukoviesti. */
    var support by mutableStateOf<SupportKind?>(null)
        private set
    /** Oliko ajastin käynnissä, kun apua tai taukoa pyydettiin? */
    var resumeAfterSupport by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set
    var deadline by mutableLongStateOf(0L)
        private set
    var remaining by mutableLongStateOf(0L)
        private set
    private var stepVoicePlayed by mutableStateOf(false)

    val currentStep: ProgramStep? get() = program.steps.getOrNull(stepIndex)
    val nextStep: ProgramStep? get() = program.steps.getOrNull(stepIndex + 1)
    val isActive: Boolean get() = currentStep != null

    /** Jatkaa keskeytynyttä ohjelmaa (esim. sovellus suljettiin kesken). */
    fun resume(saved: DayProgram) {
        program = saved
        stepIndex = progress.stepIndex.coerceIn(0, (saved.steps.size - 1).coerceAtLeast(0))
        subStepIndex = progress.subStepIndex.coerceAtLeast(0)
        running = timer.isRunning()
        remaining = timer.remaining(0L)
        deadline = timer.now() + remaining
        stepVoicePlayed = false
        support = null
    }

    fun start(newProgram: DayProgram) {
        if (newProgram.steps.isEmpty()) return
        program = newProgram
        stepIndex = 0
        subStepIndex = 0
        support = null
        running = false
        val first = newProgram.steps.first()
        timer.stop(first.minutes * 60_000L)
        deadline = 0L
        remaining = first.minutes * 60_000L
        progress.save(newProgram.id, 0, 0)
        enterStep(first)
    }

    private fun enterStep(step: ProgramStep) {
        stepVoicePlayed = step.mode != StepMode.DURATION
        if (stepVoicePlayed && step.autoPlayVoice) playVoice(step.voicePath)
    }

    /** Valmis tai Ohita. Palauttaa true, kun koko ohjelma on suoritettu. */
    fun next(): Boolean {
        val count = currentStep?.subSteps?.size ?: 0
        if (count > 0 && subStepIndex < count - 1) {
            subStepIndex += 1
            progress.save(program.id, stepIndex, subStepIndex)
            return false
        }
        return advance()
    }

    private fun advance(): Boolean {
        running = false
        timer.stop(0L)
        if (stepIndex + 1 >= program.steps.size) {
            progress.clear()
            return true
        }
        stepIndex += 1
        subStepIndex = 0
        progress.save(program.id, stepIndex, 0)
        val step = program.steps[stepIndex]
        remaining = step.minutes * 60_000L
        deadline = 0L
        enterStep(step)
        return false
    }

    fun pause() {
        if (!running) return
        remaining = timer.remaining(remaining)
        timer.stop(remaining)
        running = false
    }

    fun startTimer() {
        val step = currentStep ?: return
        if (!timer.allowed()) {
            timer.requestPermissions()
            return
        }
        if (remaining <= 0L) remaining = step.minutes * 60_000L
        deadline = timer.start(remaining, step.name)
        running = true
        if (!stepVoicePlayed) {
            stepVoicePlayed = true
            if (step.autoPlayVoice) playVoice(step.voicePath)
        }
    }

    /** Tarvitsen apua / tauon: aina sallittu; pysäyttää ajan ja näyttää viestin. */
    fun requestSupport(kind: SupportKind) {
        resumeAfterSupport = running
        pause()
        support = kind
    }

    /** Jatka viestistä: aika jatkuu, jos se oli käynnissä pyynnön hetkellä. */
    fun continueAfterSupport() {
        support = null
        if (resumeAfterSupport && remaining > 0L) startTimer()
        resumeAfterSupport = false
    }

    /** Takaisin-ele viestissä: sulkee viestin, aika pysyy pysäytettynä. */
    fun closeSupport() {
        support = null
        resumeAfterSupport = false
    }

    /** Ohjaajan lisäaika. */
    fun extend(extraMs: Long, fallbackTitle: String) {
        val left = if (running) timer.remaining(remaining) else remaining
        val extended = left + extraMs
        if (running) deadline = timer.start(extended, currentStep?.name ?: fallbackTitle)
        else timer.stop(extended)
        remaining = extended
    }

    /** Ohjaaja lopettaa ohjelman. */
    fun end() {
        running = false
        timer.stop(remaining)
        progress.clear()
        support = null
    }

    /** Päivittää jäljellä olevan ajan; kutsutaan säännöllisesti ajastimen ollessa käynnissä. */
    fun tick(): Tick {
        val left = (deadline - timer.now()).coerceAtLeast(0L)
        remaining = left
        if (left > 0L) return Tick.RUNNING
        running = false
        return if (timer.complete()) Tick.ENDED else Tick.ENDED_SILENT
    }

    companion object {
        /** Tila säilyy näytön kääntämisen ja muiden konfiguraatiomuutosten yli. */
        fun saver(timer: TimerPort, progress: RunProgress, playVoice: (String) -> Unit) =
            Saver<ProgramRunController, List<Any?>>(
                save = {
                    listOf(
                        serializeDraft(it.program), it.stepIndex, it.subStepIndex, it.support?.name,
                        it.resumeAfterSupport, it.running, it.stepVoicePlayed, it.deadline, it.remaining
                    )
                },
                restore = { v ->
                    ProgramRunController(timer, progress, playVoice).apply {
                        program = parseDraft(v[0] as String)
                        stepIndex = v[1] as Int
                        subStepIndex = v[2] as Int
                        support = (v[3] as String?)?.let { SupportKind.valueOf(it) }
                        resumeAfterSupport = v[4] as Boolean
                        running = v[5] as Boolean
                        stepVoicePlayed = v[6] as Boolean
                        deadline = v[7] as Long
                        remaining = v[8] as Long
                    }
                }
            )
    }
}

/** [RunProgress] SharedPreferencesissa (avaimet ennallaan vanhoista versioista). */
internal class PrefsRunProgress(private val prefs: SharedPreferences) : RunProgress {
    constructor(context: Context) : this(
        context.getSharedPreferences("aikapolku_program_progress_v1", Context.MODE_PRIVATE)
    )

    override val programId: String get() = prefs.getString("program_id", "") ?: ""
    override val stepIndex: Int get() = prefs.getInt("step_index", 0)
    override val subStepIndex: Int get() = prefs.getInt("sub_step_index", 0)

    override fun save(programId: String, stepIndex: Int, subStepIndex: Int) {
        prefs.edit().putString("program_id", programId)
            .putInt("step_index", stepIndex).putInt("sub_step_index", subStepIndex).apply()
    }

    override fun clear() {
        prefs.edit().remove("program_id").remove("step_index").remove("sub_step_index").apply()
    }
}
