package fi.aikapolku.app.widget

import android.content.Context
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.week.resolveEvent
import java.time.LocalDate
import java.time.LocalDateTime

/** Widgetin kortti: nimi, kuva (kortti tai ohjelman ensimmäinen kortti) ja alkamisaika. */
internal data class WidgetCard(
    val title: String,
    val imageName: String,
    val imagePath: String,
    /** Päiväohjelman nimi (luokka), tai null kortille. */
    val programTitle: String?,
    val time: String
)

internal enum class WidgetState { ACTIVE, GAP, DONE, EMPTY, OFF }

/**
 * Mitä widget näyttää. [now] = Nyt-kortti, [until] = milloin se päättyy (HH:mm), [next] =
 * Seuraavaksi. Ei asukkaan omaa vointia eikä muuta arkaluonteista: vain päivän kortit.
 */
internal data class WidgetContent(
    val state: WidgetState,
    val now: WidgetCard? = null,
    val until: String? = null,
    val next: WidgetCard? = null
)

/** Widgetin sisältö samoista tiedoista kuin Tänään-lista (muutokset, kuittaukset, ryhmät). */
internal object WidgetDay {
    const val ENABLED_KEY = "client_widget"

    fun enabled(context: Context, profileId: String = ProfileStore.currentId(context)): Boolean =
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS, profileId).getBoolean(ENABLED_KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putBoolean(ENABLED_KEY, on).apply()
    }

    fun compute(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetContent {
        val app = context.applicationContext
        if (!enabled(app)) return WidgetContent(WidgetState.OFF)
        val date = now.toLocalDate()
        val plan = ClientWeek.load(app)
        val programs = ProgramStore(app).load()
        val changes = plan.withGroupChanges(ChangeStore(app).load())
        val items = DayChanges.entries(plan.eventsFor(date), changes, date, now)
            .map { it to resolveEvent(it.event, programs) }
            .filter { (_, r) -> r.title.isNotBlank() }
        if (items.isEmpty()) return WidgetContent(WidgetState.EMPTY)

        val acks = AckStore(app)
        val skipped = items.filter { (e, _) -> e.cancelled || acks.ackTime(date, e.event.id) != null }.map { it.first.event.id }.toSet()
        val events = items.map { it.first.event }
        val current = WeekSchedule.currentIndex(events, now.toLocalTime(), skipped)
        if (current < 0) return WidgetContent(WidgetState.DONE)

        fun card(index: Int): WidgetCard {
            val (entry, r) = items[index]
            return WidgetCard(r.title, r.imageName, r.imagePath, r.title.takeIf { r.isProgram }, entry.event.time)
        }
        val nowMinutes = now.hour * 60 + now.minute
        val currentEvent = events[current]
        // Seuraava tekemätön, perumaton tapahtuma korostetun jälkeen.
        val nextIndex = (current + 1 until items.size).firstOrNull { events[it].id !in skipped }

        if (WeekSchedule.minutesOf(currentEvent.time) <= nowMinutes) {
            val until = currentEvent.until.takeIf { currentEvent.ongoing && it.isNotBlank() }
                ?: nextIndex?.let { events[it].time }
            return WidgetContent(WidgetState.ACTIVE, card(current), until, nextIndex?.let { card(it) })
        }
        // Väliaika: seuraava ei ole vielä alkanut. Nyt-kortiksi asukkaan väliajan kortti (esim. Vapaa-aika).
        val gapId = ClientViewPrefs.load(app).gapCard
        val gapTemplate = CardArt.templates.firstOrNull { it.id == gapId }
        val gap = gapTemplate?.let { WidgetCard(app.getString(it.nameRes), app.getString(it.nameRes), "builtin:${it.id}", null, "") }
        return WidgetContent(WidgetState.GAP, gap, currentEvent.time, card(current))
    }

    /**
     * Seuraava hetki, jolloin widgetin sisältö voi vaihtua: tapahtuman alku tai kestävän tapahtuman
     * loppu tänään, muuten seuraavan päivän alku.
     */
    fun nextChange(context: Context, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val app = context.applicationContext
        val date = now.toLocalDate()
        val nowMinutes = now.hour * 60 + now.minute
        val times = runCatching {
            val plan = ClientWeek.load(app)
            val changes = plan.withGroupChanges(ChangeStore(app).load())
            DayChanges.entries(plan.eventsFor(date), changes, date, null)
                .flatMap { e -> listOfNotNull(e.event.time, e.event.until.takeIf { it.isNotBlank() }) }
                .map { WeekSchedule.minutesOf(it) }
                .filter { it > nowMinutes }
        }.getOrDefault(emptyList())
        val next = times.minOrNull()
        return if (next != null) date.atStartOfDay().plusMinutes(next.toLong())
        else LocalDate.from(date).plusDays(1).atStartOfDay().plusMinutes(1)
    }
}
