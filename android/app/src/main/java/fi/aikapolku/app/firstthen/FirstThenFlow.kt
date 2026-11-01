package fi.aikapolku.app.firstthen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.timer.Tick
import fi.aikapolku.app.timer.TimerPort

/**
 * Ensin–Sitten-kortit. Tyhjä nimi tarkoittaa "ei valittua toimintoa" kielestä riippumatta;
 * näkymät näyttävät sen tekstinä "Valitse toiminto".
 */
internal class FirstThenCards(initial: FirstThenSelection) {
    var selection by mutableStateOf(normalized(initial))
        private set

    val hasNext: Boolean get() = selection.nextName.isNotBlank()

    fun update(change: (FirstThenSelection) -> FirstThenSelection) {
        selection = change(selection)
    }

    /** Ensin on tehty: Sitten-kortista tulee nykyinen toiminto. False, jos seuraavaa ei ole valittu. */
    fun promoteNext(): Boolean {
        if (!hasNext) return false
        val s = selection
        selection = s.copy(
            firstName = s.nextName, firstPhoto = s.nextPhoto, firstDescription = s.nextDescription,
            firstVoice = s.nextVoice, firstVoiceAuto = s.nextVoiceAuto
        )
        return true
    }

    /** Ohjaaja lisää vielä yhden vaiheen: Sitten-kortti tyhjennetään valittavaksi. */
    fun clearNext() {
        selection = selection.copy(nextName = "", nextPhoto = "", nextDescription = "", nextVoice = "", nextVoiceAuto = false)
    }

    companion object {
        /** Vanhat versiot tallensivat valitsemattoman kortin tekstinä "Valitse toiminto". */
        private const val LEGACY_PLACEHOLDER = "Valitse toiminto"

        fun normalized(s: FirstThenSelection) = s.copy(
            firstName = s.firstName.takeUnless { it == LEGACY_PLACEHOLDER } ?: "",
            nextName = s.nextName.takeUnless { it == LEGACY_PLACEHOLDER } ?: ""
        )

        val saver = Saver<FirstThenCards, List<Any>>(
            save = {
                val s = it.selection
                listOf(s.firstName, s.nextName, s.firstPhoto, s.nextPhoto, s.firstDescription, s.nextDescription,
                    s.firstVoice, s.nextVoice, s.firstVoiceAuto, s.nextVoiceAuto)
            },
            restore = { v ->
                FirstThenCards(FirstThenSelection(
                    v[0] as String, v[1] as String, v[2] as String, v[3] as String, v[4] as String,
                    v[5] as String, v[6] as String, v[7] as String, v[8] as Boolean, v[9] as Boolean
                ))
            }
        )
    }
}

/**
 * Ajastin-näkymän ja Ensin–Sitten-kulun ajastin (omistaja "timer").
 * [activeTask] on Ensin-toiminto, jota ajastetaan; tyhjä, kun ajastinta käytetään yksinään.
 */
internal class FlowTimer(private val timer: TimerPort) {
    var duration by mutableLongStateOf(DEFAULT_MS)
        private set
    var originalDuration by mutableLongStateOf(DEFAULT_MS)
        private set
    var remaining by mutableLongStateOf(timer.remaining(DEFAULT_MS))
        private set
    var running by mutableStateOf(timer.isRunning())
        private set
    var deadline by mutableLongStateOf(timer.now() + timer.remaining(DEFAULT_MS))
        private set
    var activeTask by mutableStateOf("")
    private var voiceStarted by mutableStateOf(false)

    /** Uusi aika kellotaululta. */
    fun setMinutes(minutes: Int) {
        running = false
        timer.stop(minutes * 60_000L)
        voiceStarted = false
        duration = minutes * 60_000L
        originalDuration = duration
        remaining = duration
    }

    /**
     * Aloita / Tauko. [title] on ilmoituksen otsikko yksinään käytettäessä.
     * [onActivityStart] kutsutaan kerran, kun Ensin-toiminnon ajastus alkaa (puheviesti).
     */
    fun startPause(title: String, onActivityStart: () -> Unit) {
        when {
            running -> {
                remaining = timer.remaining(remaining)
                timer.stop(remaining)
                running = false
            }
            !timer.allowed() -> timer.requestPermissions()
            else -> {
                if (remaining == 0L) {
                    duration = originalDuration
                    remaining = originalDuration
                }
                deadline = timer.start(remaining, activeTask.ifBlank { title })
                running = true
                if (activeTask.isNotBlank() && !voiceStarted) {
                    voiceStarted = true
                    onActivityStart()
                }
            }
        }
    }

    fun reset() {
        running = false
        timer.stop(originalDuration)
        voiceStarted = false
        duration = originalDuration
        remaining = originalDuration
    }

    /** Ensin-toiminnon ajastus kellotaulusta. Aika käynnistetään erikseen. */
    fun prepareForActivity(task: String, minutes: Int) {
        running = false
        timer.stop(minutes * 60_000L)
        duration = minutes * 60_000L
        originalDuration = duration
        remaining = duration
        activeTask = task
        deadline = 0L
        voiceStarted = false
    }

    /** Aika valmis, siirrytään eteenpäin. */
    fun finishActivity() {
        running = false
        timer.stop(0L)
        activeTask = ""
    }

    /** Ajastimesta palataan Ensin–Sitteniin: aika pysähtyy ja jäljellä oleva aika säilyy. */
    fun stopKeepingRemaining() {
        if (running) {
            remaining = (deadline - timer.now()).coerceAtLeast(0L)
            running = false
        }
        timer.stop(remaining)
    }

    /** Ensin tehty ilman aikaa. */
    fun stopForManual() {
        running = false
        timer.stop(remaining)
        activeTask = ""
    }

    fun markStopped() {
        running = false
    }

    fun tick(): Tick {
        val left = (deadline - timer.now()).coerceAtLeast(0L)
        remaining = left
        if (left > 0L) return Tick.RUNNING
        running = false
        return if (timer.complete()) Tick.ENDED else Tick.ENDED_SILENT
    }

    companion object {
        const val DEFAULT_MS = 300_000L

        fun saver(timer: TimerPort) = Saver<FlowTimer, List<Any>>(
            save = { listOf(it.duration, it.originalDuration, it.remaining, it.running, it.deadline, it.activeTask, it.voiceStarted) },
            restore = { v ->
                FlowTimer(timer).apply {
                    duration = v[0] as Long
                    originalDuration = v[1] as Long
                    remaining = v[2] as Long
                    running = v[3] as Boolean
                    deadline = v[4] as Long
                    activeTask = v[5] as String
                    voiceStarted = v[6] as Boolean
                }
            }
        )
    }
}
