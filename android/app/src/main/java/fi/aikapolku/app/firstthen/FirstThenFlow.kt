package fi.aikapolku.app.firstthen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
internal class FirstThenCards(initial: FirstThenSelection, original: FirstThenSelection? = null) {
    var selection by mutableStateOf(normalized(initial))
        private set

    /**
     * Pari sellaisena kuin kulku aloitettiin. Talletetaan ensimmäisellä siirrolla, jotta
     * kulun päätyttyä Ensin–Sitten näyttää taas alkuperäiset kortit eikä kahta samaa.
     */
    var original by mutableStateOf(original)
        private set

    val hasNext: Boolean get() = selection.nextName.isNotBlank()

    fun update(change: (FirstThenSelection) -> FirstThenSelection) {
        selection = change(selection)
    }

    /** Ensin on tehty: Sitten-kortista tulee nykyinen toiminto. False, jos seuraavaa ei ole valittu. */
    fun promoteNext(): Boolean {
        if (!hasNext) return false
        val s = selection
        if (original == null) original = s
        selection = s.copy(
            firstName = s.nextName, firstPhoto = s.nextPhoto, firstDescription = s.nextDescription,
            firstVoice = s.nextVoice, firstVoiceAuto = s.nextVoiceAuto,
            nextAltName = "", nextAltPhoto = "", nextAltVoice = ""
        )
        return true
    }

    /** Sitten-kortilla on toinen vaihtoehto: asiakas valitsee ennen siirtymistä. */
    val hasChoice: Boolean get() = hasNext && selection.nextAltName.isNotBlank()

    /**
     * Asiakas valitsi Sitten-kohdan. [useAlternative] = toinen vaihtoehto korvaa Sitten-kortin.
     * Alkuperäinen pari talletetaan, jotta kulun jälkeen näkyvät taas omat korttinsa.
     */
    fun chooseNext(useAlternative: Boolean) {
        val s = selection
        if (s.nextAltName.isBlank()) return
        if (original == null) original = s
        selection = if (useAlternative) s.copy(
            nextName = s.nextAltName, nextPhoto = s.nextAltPhoto, nextDescription = "",
            nextVoice = s.nextAltVoice, nextVoiceAuto = false,
            nextAltName = "", nextAltPhoto = "", nextAltVoice = ""
        ) else s.copy(nextAltName = "", nextAltPhoto = "", nextAltVoice = "")
    }

    /** Ohjaaja lisää vielä yhden vaiheen: Sitten-kortti tyhjennetään valittavaksi. */
    fun clearNext() {
        selection = selection.copy(
            nextName = "", nextPhoto = "", nextDescription = "", nextVoice = "", nextVoiceAuto = false,
            nextAltName = "", nextAltPhoto = "", nextAltVoice = ""
        )
    }

    /** Kulku päättyi (valmis tai poistuttiin aloitukseen): alkuperäinen pari takaisin. */
    fun finishFlow() {
        original?.let { selection = it }
        original = null
    }

    companion object {
        /** Vanhat versiot tallensivat valitsemattoman kortin tekstinä "Valitse toiminto". */
        private const val LEGACY_PLACEHOLDER = "Valitse toiminto"

        fun normalized(s: FirstThenSelection) = s.copy(
            firstName = s.firstName.takeUnless { it == LEGACY_PLACEHOLDER } ?: "",
            nextName = s.nextName.takeUnless { it == LEGACY_PLACEHOLDER } ?: ""
        )

        private const val FIELDS = 13

        private fun fields(s: FirstThenSelection): List<Any> = listOf(
            s.firstName, s.nextName, s.firstPhoto, s.nextPhoto, s.firstDescription, s.nextDescription,
            s.firstVoice, s.nextVoice, s.firstVoiceAuto, s.nextVoiceAuto, s.nextAltName, s.nextAltPhoto, s.nextAltVoice
        )

        private fun selectionOf(v: List<Any>, at: Int, size: Int) = FirstThenSelection(
            v[at] as String, v[at + 1] as String, v[at + 2] as String, v[at + 3] as String, v[at + 4] as String,
            v[at + 5] as String, v[at + 6] as String, v[at + 7] as String, v[at + 8] as Boolean, v[at + 9] as Boolean,
            if (size >= 13) v[at + 10] as String else "",
            if (size >= 13) v[at + 11] as String else "",
            if (size >= 13) v[at + 12] as String else ""
        )

        /** Nykyinen pari (13 kenttää) ja kulun alkuperäinen pari, jos kulku on kesken (+13). Vanha muoto 10/20. */
        val saver = Saver<FirstThenCards, List<Any>>(
            save = { cards -> fields(cards.selection) + (cards.original?.let { fields(it) } ?: emptyList()) },
            restore = { v ->
                val size = if (v.size % FIELDS == 0) FIELDS else 10
                FirstThenCards(selectionOf(v, 0, size), if (v.size >= 2 * size) selectionOf(v, size, size) else null)
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
    /** Montako kertaa asiakas on saanut lisäaikaa tähän toimintoon ("Tarvitsen vielä hetken", A2). */
    var moreTimeUsed by mutableIntStateOf(0)
        private set
    /** Ensin kuitattiin valmiiksi ennen ajan loppua (ei lisäaikaa eikä äänimerkkiä). */
    var doneEarly by mutableStateOf(false)
        private set

    /**
     * Ensin-toiminnon ajastus on aloitettu eikä vielä kuitattu (käynnissä, tauolla tai aika
     * päättynyt). Silloin ajastimeen palataan eikä aikaa aloiteta alusta.
     */
    val inProgress: Boolean get() = activeTask.isNotBlank() && (running || remaining < duration)

    /** Uusi aika kellotaululta. */
    fun setMinutes(minutes: Int) {
        running = false
        timer.stop(minutes * 60_000L)
        voiceStarted = false
        moreTimeUsed = 0
        doneEarly = false
        duration = minutes * 60_000L
        originalDuration = duration
        remaining = duration
    }

    /**
     * Aloita / Tauko. [title] on ilmoituksen otsikko yksinään käytettäessä.
     * [onActivityStart] kutsutaan kerran, kun Ensin-toiminnon ajastus alkaa (puheviesti).
     * [askPermissions] = saa pyytää puuttuvia lupia ennen käynnistystä (ohjaaja). Asiakkaalle
     * false: aika alkaa heti ilman lupia, eikä Androidin lupakyselyitä avata asiakkaan eteen
     * (puhelintesti 15.11.2026: lupakysely tuli ja aika jäi käynnistymättä).
     */
    fun startPause(title: String, askPermissions: Boolean = true, onActivityStart: () -> Unit) {
        when {
            running -> {
                remaining = timer.remaining(remaining)
                timer.stop(remaining)
                running = false
            }
            askPermissions && !timer.allowed() -> timer.requestPermissions()
            else -> {
                if (remaining == 0L) {
                    duration = originalDuration
                    remaining = originalDuration
                    moreTimeUsed = 0
                    doneEarly = false
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
        moreTimeUsed = 0
        doneEarly = false
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
        moreTimeUsed = 0
        doneEarly = false
    }

    /** Aika valmis, siirrytään eteenpäin. */
    fun finishActivity() {
        running = false
        timer.stop(0L)
        activeTask = ""
        moreTimeUsed = 0
        doneEarly = false
    }

    /**
     * Ensin valmis ennen ajan loppua (asiakkaan Valmis asetuksen rajoissa tai ohjaaja):
     * aika lopetetaan ilman äänimerkkiä ja näkymä näyttää saman valmis-kortin kuin ajan päättyessä.
     */
    fun finishEarly() {
        if (activeTask.isBlank()) return
        running = false
        remaining = 0L
        doneEarly = true
        timer.stop(0L)
    }

    /**
     * "Tarvitsen vielä hetken" (A2): lisää [extraMs] käynnissä olevaan aikaan tai käynnistää ajan
     * uudelleen, jos se on jo loppunut. Pysäytettyyn aikaan ei lisätä (ohjaaja säätää sen itse).
     * Rajat (montako kertaa, kuinka paljon) tarkistaa näkymä MoreTime-säännöillä.
     * Palauttaa true, jos aikaa lisättiin.
     */
    fun addMoreTime(extraMs: Long): Boolean {
        if (activeTask.isBlank() || extraMs <= 0L || doneEarly) return false
        val left = if (running) (deadline - timer.now()).coerceAtLeast(0L) else remaining
        if (!running && left > 0L) return false
        val newLeft = left + extraMs
        // Kesto kasvaa tarvittaessa, jotta rengas ja kellotaulu näyttävät lisätyn ajan oikein.
        if (newLeft > duration) duration = newLeft
        remaining = newLeft
        deadline = timer.start(newLeft, activeTask)
        running = true
        doneEarly = false
        moreTimeUsed += 1
        return true
    }

    /** Pysäyttää ajan säilyttäen jäljellä olevan ajan (ohjaajan tauko). */
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
        moreTimeUsed = 0
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
            save = { listOf(it.duration, it.originalDuration, it.remaining, it.running, it.deadline, it.activeTask, it.voiceStarted, it.moreTimeUsed, it.doneEarly) },
            restore = { v ->
                FlowTimer(timer).apply {
                    duration = v[0] as Long
                    originalDuration = v[1] as Long
                    remaining = v[2] as Long
                    running = v[3] as Boolean
                    deadline = v[4] as Long
                    activeTask = v[5] as String
                    voiceStarted = v[6] as Boolean
                    moreTimeUsed = v.getOrNull(7) as? Int ?: 0
                    doneEarly = v.getOrNull(8) as? Boolean ?: false
                }
            }
        )
    }
}

/**
 * Ensin–Sitten-ajastimen Valmis ennen ajan loppua: sama asiakaskohtainen Valmis-asetus kuin
 * päiväohjelmassa (RunPreferences.DONE_*). null = ei painiketta (asiakas: vasta ajan päätyttyä),
 * ALLOW = kuittaus heti, CONFIRM = kysy ensin. Ohjaaja (PIN) saa aina, kysyen. Jos asiakas on
 * pyytänyt lisäaikaa (A2), hän saa lopettaa heti, kun on valmis (kuten päiväohjelmassa).
 */
internal object EarlyDone {
    fun gate(policy: String, caregiverOpen: Boolean, moreTimeUsed: Int): fi.aikapolku.app.programs.run.DoneGate? = when {
        caregiverOpen -> fi.aikapolku.app.programs.run.DoneGate.CONFIRM
        moreTimeUsed > 0 -> fi.aikapolku.app.programs.run.DoneGate.ALLOW
        policy == fi.aikapolku.app.data.RunPreferences.DONE_ALWAYS -> fi.aikapolku.app.programs.run.DoneGate.ALLOW
        policy == fi.aikapolku.app.data.RunPreferences.DONE_AFTER_TIME -> null
        else -> fi.aikapolku.app.programs.run.DoneGate.CONFIRM
    }
}
