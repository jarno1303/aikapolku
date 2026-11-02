package fi.aikapolku.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/** Muutoksen laji: peruttu, tilalle muuta tai uusi aika (saman päivän sisällä). */
internal enum class ChangeType(val key: String) {
    CANCEL("cancel"),
    REPLACE("replace"),
    MOVE("move");

    companion object {
        fun from(key: String?): ChangeType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Milloin käyttäjä näkee muutoksen. Valitaan muutoskohtaisesti, koska osaa asiakkaista
 * etukäteistieto rauhoittaa ja osaa se ahdistaa. Ohjaaja näkee muutoksen aina heti.
 */
internal enum class ChangeReveal(val key: String) {
    NOW("now"),
    MORNING("morning"),
    AT_EVENT("event");

    companion object {
        fun from(key: String?): ChangeReveal = entries.firstOrNull { it.key == key } ?: NOW
    }
}

/**
 * Yhden päivän muutos viikko-ohjelman tapahtumaan ([eventId]). Viikko-ohjelma ei muutu.
 * Korvaava sisältö ([kind], [programId], [cardName], [cardPhoto]) vain lajilla REPLACE,
 * uusi kellonaika ([time]) vain lajilla MOVE.
 */
internal data class EventChange(
    val id: String,
    val date: LocalDate,
    val eventId: String,
    val type: ChangeType,
    val kind: EventKind = EventKind.CARD,
    val programId: String = "",
    val cardName: String = "",
    val cardPhoto: String = "",
    val time: String = "",
    val reason: String = "",
    val voicePath: String = "",
    val autoPlayVoice: Boolean = false,
    val reveal: ChangeReveal = ChangeReveal.NOW,
    /** Näytetäänkö käyttäjälle, mikä korvattiin (yliviivattuna). Perutussa aina. */
    val showOriginal: Boolean = true,
    /**
     * Päiväohjelman yksittäinen vaihe ([stepId]); tyhjä = koko tapahtuma. Vaiheen nimi ja kuva
     * talletetaan muutokseen näyttämistä varten. Vaihemuutos: vain CANCEL tai REPLACE (kortti).
     */
    val stepId: String = "",
    val stepName: String = "",
    val stepPhoto: String = ""
) {
    val isStep: Boolean get() = stepId.isNotBlank()

    /** Kuittausavain AckStoreen ("Selvä" muutosnäkymässä). */
    val ackId: String get() = "change:$id"
}

/** Päivän rivi muutosten jälkeen: voimassa oleva tapahtuma, alkuperäinen ja näkyvä muutos. */
internal data class DayEntry(
    val event: WeekEvent,
    val original: WeekEvent,
    val change: EventChange?,
    /** Päiväohjelman vaiheiden muutokset (näkyvät). */
    val stepChanges: List<EventChange> = emptyList()
) {
    val cancelled: Boolean get() = change?.type == ChangeType.CANCEL
}

/** Muutosten soveltaminen päivään: puhdas logiikka, testattavissa ilman laitetta. */
internal object DayChanges {
    const val KEEP_DAYS = 7L

    /** Tapahtuma muutoksen jälkeen. Vaihemuutos ei muuta tapahtumaa (vain ohjelman vaihetta). */
    fun effective(original: WeekEvent, change: EventChange): WeekEvent = if (change.isStep) original else when (change.type) {
        ChangeType.CANCEL -> original
        ChangeType.REPLACE -> original.copy(
            kind = change.kind,
            programId = if (change.kind == EventKind.PROGRAM) change.programId else "",
            cardName = if (change.kind == EventKind.CARD) change.cardName else "",
            cardPhoto = if (change.kind == EventKind.CARD) change.cardPhoto else ""
        )
        ChangeType.MOVE -> original.copy(time = change.time.ifBlank { original.time })
    }

    /**
     * Hetki, josta alkaen käyttäjä näkee muutoksen. "Tapahtuman aikaan" tarkoittaa
     * aikaisinta näistä: alkuperäinen aika, uusi aika ja tapahtuman ennakkomuistutus,
     * jotta muistutus ei kerro vanhasta tapahtumasta.
     */
    fun revealAt(change: EventChange, original: WeekEvent): LocalDateTime? = when (change.reveal) {
        ChangeReveal.NOW -> null
        ChangeReveal.MORNING -> change.date.atStartOfDay()
        ChangeReveal.AT_EVENT -> {
            val times = listOfNotNull(
                WeekSchedule.minutesOf(original.time),
                change.time.takeIf { change.type == ChangeType.MOVE && it.isNotBlank() }?.let(WeekSchedule::minutesOf)
            )
            val before = if (original.notify) original.notifyBefore.coerceIn(0, 60) else 0
            change.date.atStartOfDay().plusMinutes((times.min() - before).toLong())
        }
    }

    fun isRevealed(change: EventChange, original: WeekEvent, now: LocalDateTime): Boolean =
        revealAt(change, original)?.let { !now.isBefore(it) } ?: true

    /**
     * Päivän rivit aikajärjestyksessä. [now] = käyttäjän näkymä (vain jo näkyvät muutokset);
     * null = kaikki muutokset (ohjaaja, muistutukset). Perutut ovat mukana merkittyinä.
     */
    fun entries(events: List<WeekEvent>, changes: List<EventChange>, date: LocalDate, now: LocalDateTime?): List<DayEntry> =
        events.map { e ->
            val own = changes.filter { it.date == date && it.eventId == e.id && (now == null || isRevealed(it, e, now)) }
            val change = own.firstOrNull { !it.isStep }
            // Koko tapahtuman muutos korvaa vaihemuutokset (esim. peruttu ohjelma).
            val steps = if (change == null) own.filter { it.isStep } else emptyList()
            DayEntry(if (change != null) effective(e, change) else e, e, change, steps)
        }.sortedWith(compareBy({ WeekSchedule.minutesOf(it.event.time) }, { it.event.id }))

    /** Muutokset, jotka näytetään käyttäjälle aloitusnäkymässä: näkyvät ja kuittaamattomat. */
    fun announced(
        plan: WeekPlan,
        changes: List<EventChange>,
        now: LocalDateTime,
        acked: (EventChange) -> Boolean
    ): List<Pair<EventChange, WeekEvent>> {
        val today = now.toLocalDate()
        return listOf(today, today.plusDays(1)).flatMap { date ->
            WeekSchedule.eventsFor(plan, date).flatMap { e ->
                changes.filter { it.date == date && it.eventId == e.id && isRevealed(it, e, now) && !acked(it) }
                    .sortedBy { it.isStep }
                    .map { it to e }
            }
        }
    }

    /** Vanhat (yli viikon) ja tapahtumaa vailla olevat muutokset pois. */
    fun cleaned(changes: List<EventChange>, today: LocalDate, eventIds: Set<String>?): List<EventChange> =
        changes.filter { !it.date.isBefore(today.minusDays(KEEP_DAYS)) && (eventIds == null || it.eventId in eventIds) }

    /** Tallentaa muutoksen; samalle päivälle, tapahtumalle ja vaiheelle on vain yksi muutos. */
    fun upsert(changes: List<EventChange>, change: EventChange): List<EventChange> =
        changes.filterNot {
            it.id == change.id || (it.date == change.date && it.eventId == change.eventId && it.stepId == change.stepId)
        } + change

    /** Tapahtuman koko muutos (ei vaihemuutos) päivälle. */
    fun wholeChange(changes: List<EventChange>, date: LocalDate, eventId: String): EventChange? =
        changes.firstOrNull { it.date == date && it.eventId == eventId && !it.isStep }

    /**
     * Päiväohjelman suoritus: päivän vaihemuutokset ohjelman vaiheisiin. Peruttua vaihetta ei
     * poisteta (keskeytetty ohjelma jatkuu oikeasta kohdasta), vaan se merkitään ohitettavaksi.
     * [substeps] = valmiit osavaiheet korvaavalle kortille.
     */
    fun applyToProgram(
        program: DayProgram,
        stepChanges: List<EventChange>,
        substeps: (name: String, photo: String) -> List<String>
    ): DayProgram {
        if (stepChanges.isEmpty()) return program
        return program.copy(steps = program.steps.map { step ->
            val c = stepChanges.firstOrNull { it.stepId == step.id } ?: return@map step
            val from = if (c.showOriginal || c.type == ChangeType.CANCEL) step.name else ""
            when (c.type) {
                ChangeType.CANCEL -> step.copy(changedToday = true, cancelledToday = true, changedFrom = from)
                ChangeType.REPLACE -> step.copy(
                    name = c.cardName,
                    photo = c.cardPhoto,
                    description = "",
                    voicePath = "",
                    autoPlayVoice = false,
                    subSteps = substeps(c.cardName, c.cardPhoto),
                    changedToday = true,
                    changedFrom = from
                )
                ChangeType.MOVE -> step
            }
        })
    }

    /** Päivän vaihemuutokset ohjelmalle: kaikki sen päivän tapahtumat, joissa ohjelma on. */
    fun stepChangesFor(plan: WeekPlan, changes: List<EventChange>, date: LocalDate, programId: String): List<EventChange> {
        val eventIds = WeekSchedule.eventsFor(plan, date)
            .filter { it.kind == EventKind.PROGRAM && it.programId == programId && wholeChange(changes, date, it.id) == null }
            .map { it.id }.toSet()
        return changes.filter { it.date == date && it.isStep && it.eventId in eventIds }.distinctBy { it.stepId }
    }
}

/** Profiilin muutokset. */
internal class ChangeStore(context: Context, profileId: String = ProfileStore.currentId(context)) {
    private val prefs = ProfileScope.prefs(context, ProfileScope.CHANGES, profileId)

    fun load(): List<EventChange> = decode(prefs.getString(KEY, null))

    fun save(changes: List<EventChange>) {
        prefs.edit().putString(KEY, encode(changes)).commit()
    }

    fun upsert(change: EventChange) {
        save(DayChanges.cleaned(DayChanges.upsert(load(), change), LocalDate.now(), null))
        prefs.edit()
            .putString(KEY_LAST_REVEAL, change.reveal.key)
            .putBoolean(KEY_LAST_SHOW_ORIGINAL, change.showOriginal)
            .apply()
    }

    fun delete(id: String) = save(load().filterNot { it.id == id })

    /** Poistaa vanhat ja poistettuihin tapahtumiin liittyvät muutokset. */
    fun clean(today: LocalDate, eventIds: Set<String>) {
        val all = load()
        val kept = DayChanges.cleaned(all, today, eventIds)
        if (kept.size != all.size) save(kept)
    }

    /** Edellisen muutoksen valinnat ovat valmiina seuraavassa (saman asiakkaan ohjaajalle). */
    val lastReveal: ChangeReveal get() = ChangeReveal.from(prefs.getString(KEY_LAST_REVEAL, null))
    val lastShowOriginal: Boolean get() = prefs.getBoolean(KEY_LAST_SHOW_ORIGINAL, true)

    companion object {
        private const val KEY = "changes"
        private const val KEY_LAST_REVEAL = "last_reveal"
        private const val KEY_LAST_SHOW_ORIGINAL = "last_show_original"

        fun encode(changes: List<EventChange>): String {
            val array = JSONArray()
            changes.forEach { c ->
                array.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("date", c.date.toString())
                        .put("eventId", c.eventId)
                        .put("type", c.type.key)
                        .put("kind", c.kind.key)
                        .put("programId", c.programId)
                        .put("cardName", c.cardName)
                        .put("cardPhoto", c.cardPhoto)
                        .put("time", c.time)
                        .put("reason", c.reason)
                        .put("voicePath", c.voicePath)
                        .put("autoPlayVoice", c.autoPlayVoice)
                        .put("reveal", c.reveal.key)
                        .put("showOriginal", c.showOriginal)
                        .put("stepId", c.stepId)
                        .put("stepName", c.stepName)
                        .put("stepPhoto", c.stepPhoto)
                )
            }
            return array.toString()
        }

        fun decode(text: String?): List<EventChange> {
            if (text.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val eventId = o.optString("eventId")
                val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
                val type = ChangeType.from(o.optString("type"))
                if (id.isBlank() || eventId.isBlank() || date == null || type == null) return@mapNotNull null
                val time = o.optString("time").takeIf { it.isNotBlank() }
                    ?.let { WeekSchedule.minutesOf(it).let { m -> WeekSchedule.formatTime(m / 60, m % 60) } } ?: ""
                EventChange(
                    id = id,
                    date = date,
                    eventId = eventId,
                    type = type,
                    kind = EventKind.from(o.optString("kind")),
                    programId = o.optString("programId"),
                    cardName = o.optString("cardName"),
                    cardPhoto = o.optString("cardPhoto"),
                    time = time,
                    reason = o.optString("reason"),
                    voicePath = o.optString("voicePath"),
                    autoPlayVoice = o.optBoolean("autoPlayVoice", false),
                    reveal = ChangeReveal.from(o.optString("reveal")),
                    showOriginal = o.optBoolean("showOriginal", true),
                    stepId = o.optString("stepId"),
                    stepName = o.optString("stepName"),
                    stepPhoto = o.optString("stepPhoto")
                )
            }
        }
    }
}
