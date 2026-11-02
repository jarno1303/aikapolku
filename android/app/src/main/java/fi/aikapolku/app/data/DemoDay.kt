package fi.aikapolku.app.data

import android.content.Context
import fi.aikapolku.app.R
import java.time.LocalDateTime

/**
 * Esittelypäivä testaukseen ja esittelyihin: päiväohjelma, joka alkoi juuri (Aamupala,
 * minuutin Hampaiden pesu, Pukeutuminen), ja myöhempi tapahtuma, jotta ohjelman jälkeen
 * näkyy väliaika. Sama sisältö puhelimessa ajettavassa automaattitestissä.
 */
internal object DemoDay {
    const val PROGRAM_ID = "demo-ohjelma"
    private const val EVENT_PREFIX = "demo-"

    internal data class Result(val programId: String, val programEventId: String, val laterEventId: String)

    /** Pelkkä sisältö (testattavissa ilman laitetta). [texts] = resurssitekstit. */
    fun program(texts: (Int) -> String): DayProgram = DayProgram(
        id = PROGRAM_ID,
        name = texts(R.string.demo_program_name),
        steps = listOf(
            ProgramStep("demo-s1", texts(R.string.card_breakfast), "builtin:breakfast", ""),
            ProgramStep("demo-s2", texts(R.string.card_brush_teeth), "builtin:brush_teeth", "", mode = StepMode.DURATION, minutes = 1),
            ProgramStep("demo-s3", texts(R.string.card_dress), "builtin:dress", "")
        ),
        timeMode = TimeMode.FREE
    )

    /** Ohjelma alkoi minuutti sitten; myöhempi tapahtuma 20 minuutin päästä (samana päivänä). */
    fun plan(existing: WeekPlan, now: LocalDateTime, texts: (Int) -> String): Pair<WeekPlan, Result> {
        val date = now.toLocalDate()
        val day = date.dayOfWeek.value
        val week = WeekSchedule.weekOf(existing, date)
        val minutes = now.hour * 60 + now.minute
        val start = (minutes - 1).coerceIn(0, 23 * 60 + 57)
        val later = (minutes + 20).coerceIn(start + 1, 23 * 60 + 59)
        val programEvent = WeekEvent(
            id = "${EVENT_PREFIX}ohjelma-$day", day = day, week = week,
            time = WeekSchedule.formatTime(start / 60, start % 60),
            kind = EventKind.PROGRAM, programId = PROGRAM_ID
        )
        val laterEvent = WeekEvent(
            id = "${EVENT_PREFIX}lukeminen-$day", day = day, week = week,
            time = WeekSchedule.formatTime(later / 60, later % 60),
            kind = EventKind.CARD, cardName = texts(R.string.card_reading), cardPhoto = "builtin:reading"
        )
        val events = existing.events.filterNot { it.id.startsWith(EVENT_PREFIX) } + programEvent + laterEvent
        return existing.copy(events = events) to Result(PROGRAM_ID, programEvent.id, laterEvent.id)
    }

    /** Täyttää profiilin [profileId] esittelypäivän sisällöllä (korvaa aiemman esittelyn). */
    fun fill(context: Context, profileId: String, now: LocalDateTime = LocalDateTime.now()): Result {
        val app = context.applicationContext
        val texts: (Int) -> String = { app.getString(it) }
        ProgramStore(app, profileId).save(program(texts))
        val store = WeekStore(app, profileId)
        val (plan, result) = plan(store.load(), now, texts)
        store.save(plan)
        // Aiemmat kuittaukset pois, jotta esittely alkaa alusta.
        AckStore(app, profileId).let { acks -> acks.prune(now.toLocalDate().plusDays(2)) }
        return result
    }

    /** Esittelyprofiili (luodaan, jos puuttuu). */
    fun profile(context: Context, name: String): Profile =
        ProfileStore.list(context).firstOrNull { it.name == name } ?: ProfileStore.add(context, name)
}
