package fi.aikapolku.app.programs.run

import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.ProfileScope
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

/** Valmis-painikkeen toiminta, kun kestotehtävän aikaa on jäljellä. */
internal enum class DoneGate {
    /** Valmis siirtää eteenpäin. */
    ALLOW,
    /** Kysy ensin ("Aikaa on vielä …"). */
    CONFIRM,
    /** Odota, kunnes aika on kulunut (painike himmeä, näyttää jäljellä olevan ajan). */
    WAIT,
    /** Aikaa ei ole käynnistetty: painike käynnistää ajan. */
    START_TIMER
}

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
    /** Onko nykyisen vaiheen aika käynnistetty (Aloita aika -tilassa Valmis näkyy vasta tämän jälkeen). */
    var timerStarted by mutableStateOf(false)
        private set
    /**
     * Käynnistyykö kestotehtävän aika itsestään, kun tehtävä tulee näkyviin (ohjaajan valinta,
     * RunPreferences.TIMER_AUTO). Näkymä asettaa tämän ennen aloitusta.
     */
    var autoStartTimer = false
    /** Ohitetut tehtävät (indeksit): näkyvät himmeinä Kaikki valmista -näkymässä. */
    var skipped by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** Milloin nykyinen vaihe tuli näkyviin (monotoninen aika): vahinkonaputusten esto. */
    private var stepEnteredAt = Long.MIN_VALUE / 2

    /** Valmis ei reagoi heti uuden vaiheen alussa, jottei nopea naputtelu vie ohjelmaa läpi. */
    fun tapGuardActive(): Boolean = timer.now() - stepEnteredAt < TAP_GUARD_MS

    /**
     * Mitä Valmis tekee, kun kestotehtävän aikaa on jäljellä. [policy] = ohjaajan valinta
     * (RunPreferences.DONE_*). Ohjaajan avaama lukko ([caregiverOpen]) sallii aina, kysyen.
     * Rajoitus koskee vain painallusta, joka päättäisi tehtävän (ei osavaiheita).
     */
    fun doneGate(policy: String, caregiverOpen: Boolean, timerStart: String = RunPreferences.TIMER_AUTO): DoneGate {
        val step = currentStep ?: return DoneGate.ALLOW
        // Aloita aika -tila: asiakas käynnistää ajan itse ennen kuin Valmis tulee esiin.
        if (step.mode == StepMode.DURATION && timerStart == RunPreferences.TIMER_BUTTON && !timerStarted && !running)
            return DoneGate.START_TIMER
        val subCount = step.subSteps.size
        if (subCount > 0 && subStepIndex < subCount - 1) return DoneGate.ALLOW
        if (step.mode != StepMode.DURATION) return DoneGate.ALLOW
        val left = timeLeft()
        if (left <= 0L) return DoneGate.ALLOW
        return when (policy) {
            RunPreferences.DONE_ALWAYS -> DoneGate.ALLOW
            RunPreferences.DONE_AFTER_TIME -> when {
                caregiverOpen -> DoneGate.CONFIRM
                running -> DoneGate.WAIT
                else -> DoneGate.START_TIMER
            }
            else -> DoneGate.CONFIRM
        }
    }

    /** Jäljellä oleva aika juuri nyt. */
    fun timeLeft(): Long = if (running) (deadline - timer.now()).coerceAtLeast(0L) else remaining

    val currentStep: ProgramStep? get() = program.steps.getOrNull(stepIndex)
    val nextStep: ProgramStep? get() = firstActive(stepIndex + 1)?.let { program.steps[it] }

    /** Ensimmäinen tänään tehtävä vaihe alkaen [from] (perutut ohitetaan). */
    private fun firstActive(from: Int): Int? =
        (from.coerceAtLeast(0) until program.steps.size).firstOrNull { !program.steps[it].cancelledToday }

    private fun cancelledIndices(p: DayProgram): Set<Int> = p.steps.indices.filter { p.steps[it].cancelledToday }.toSet()

    /** Näytettävä sijainti: perutut eivät ole mukana laskussa ("2/4"). */
    val activeIndex: Int get() = (0 until stepIndex).count { !program.steps[it].cancelledToday }
    val activeCount: Int get() = program.steps.count { !it.cancelledToday }

    /**
     * Ohjaajan muutos nykyiseen vaiheeseen kesken suorituksen: vaihe korvataan (aika ja
     * ajoitus säilyvät). Osavaiheet alkavat alusta.
     */
    fun replaceCurrent(step: ProgramStep) {
        if (currentStep == null) return
        program = program.copy(steps = program.steps.mapIndexed { i, s -> if (i == stepIndex) step else s })
        subStepIndex = 0
        stepEnteredAt = timer.now()
        progress.save(program.id, stepIndex, 0)
        if (step.autoPlayVoice && step.voicePath.isNotBlank()) playVoice(step.voicePath)
    }
    val isActive: Boolean get() = currentStep != null

    /** Jatkaa keskeytynyttä ohjelmaa (esim. sovellus suljettiin kesken). */
    fun resume(saved: DayProgram) {
        program = saved
        stepIndex = progress.stepIndex.coerceIn(0, (saved.steps.size - 1).coerceAtLeast(0))
        // Tänään peruttu vaihe ohitetaan myös jatkettaessa.
        stepIndex = firstActive(stepIndex) ?: stepIndex
        skipped = cancelledIndices(saved)
        subStepIndex = progress.subStepIndex.coerceAtLeast(0)
        running = timer.isRunning()
        timerStarted = true
        remaining = timer.remaining(0L)
        deadline = timer.now() + remaining
        stepVoicePlayed = false
        support = null
        stepEnteredAt = timer.now()
    }

    fun start(newProgram: DayProgram) {
        if (newProgram.steps.isEmpty()) return
        program = newProgram
        // Tänään perutut vaiheet ohitetaan ja näkyvät lopussa ohitettuina.
        val start = firstActive(0) ?: return
        stepIndex = start
        subStepIndex = 0
        skipped = cancelledIndices(newProgram)
        stepEnteredAt = timer.now()
        support = null
        running = false
        timerStarted = false
        val first = newProgram.steps[start]
        timer.stop(first.minutes * 60_000L)
        deadline = 0L
        remaining = first.minutes * 60_000L
        progress.save(newProgram.id, start, 0)
        enterStep(first)
    }

    private fun enterStep(step: ProgramStep) {
        stepVoicePlayed = step.mode != StepMode.DURATION
        if (stepVoicePlayed && step.autoPlayVoice) playVoice(step.voicePath)
        // Aika alkaa itsestään: ei koskaan avata Androidin lupa-asetuksia asiakkaan eteen.
        if (step.mode == StepMode.DURATION && autoStartTimer) startTimer(askPermissions = false)
    }

    /** Valmis tai Ohita. Palauttaa true, kun koko ohjelma on suoritettu. */
    fun next(): Boolean {
        val count = currentStep?.subSteps?.size ?: 0
        if (count > 0 && subStepIndex < count - 1) {
            subStepIndex += 1
            stepEnteredAt = timer.now()
            progress.save(program.id, stepIndex, subStepIndex)
            return false
        }
        return advance()
    }

    /** Ohita tehtävä kokonaan (myös jäljellä olevat osavaiheet). Kirjataan ohitetuksi. */
    fun skip(): Boolean {
        if (currentStep == null) return true
        skipped = skipped + stepIndex
        return advance()
    }

    private fun advance(): Boolean {
        running = false
        timer.stop(0L)
        val nextIndex = firstActive(stepIndex + 1)
        if (nextIndex == null) {
            progress.clear()
            return true
        }
        stepIndex = nextIndex
        subStepIndex = 0
        timerStarted = false
        stepEnteredAt = timer.now()
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

    /**
     * Käynnistää tai jatkaa vaiheen aikaa. [askPermissions] = saa pyytää puuttuvia lupia
     * (avaa Androidin asetukset); asiakkaalle false, jolloin aika alkaa ilman lupia.
     */
    fun startTimer(askPermissions: Boolean = true) {
        val step = currentStep ?: return
        if (askPermissions && !timer.allowed()) {
            timer.requestPermissions()
            return
        }
        if (remaining <= 0L) remaining = step.minutes * 60_000L
        deadline = timer.start(remaining, step.name)
        running = true
        timerStarted = true
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
        const val TAP_GUARD_MS = 1_500L

        /** Tila säilyy näytön kääntämisen ja muiden konfiguraatiomuutosten yli. */
        fun saver(timer: TimerPort, progress: RunProgress, playVoice: (String) -> Unit) =
            Saver<ProgramRunController, List<Any?>>(
                save = {
                    listOf(
                        serializeDraft(it.program), it.stepIndex, it.subStepIndex, it.support?.name,
                        it.resumeAfterSupport, it.running, it.stepVoicePlayed, it.deadline, it.remaining,
                        ArrayList(it.skipped), it.timerStarted, it.autoStartTimer
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
                        @Suppress("UNCHECKED_CAST")
                        skipped = (v.getOrNull(9) as? List<Int>)?.toSet() ?: emptySet()
                        timerStarted = v.getOrNull(10) as? Boolean ?: running
                        autoStartTimer = v.getOrNull(11) as? Boolean ?: false
                    }
                }
            )
    }
}

/** [RunProgress] SharedPreferencesissa (avaimet ennallaan vanhoista versioista). */
internal class PrefsRunProgress(private val prefs: SharedPreferences) : RunProgress {
    constructor(context: Context) : this(
        ProfileScope.prefs(context, ProfileScope.PROGRESS)
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
