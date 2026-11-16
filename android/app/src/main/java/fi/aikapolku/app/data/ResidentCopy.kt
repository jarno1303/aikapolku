package fi.aikapolku.app.data

import android.content.Context
import java.util.UUID

/** Yksikön yhteiset perusasetukset ohjatussa aloituksessa (kaikille asukkaille kerralla). */
internal data class CommonSettings(
    val timeMode: String = TimeDisplayPrefs.NUMBERS,
    val warnMinutes: Int = TimeDisplayPrefs.WARN_DEFAULT,
    val moreMinutes: Int = MoreTime.MINUTES_DEFAULT,
    val moreCount: Int = MoreTime.COUNT_DEFAULT,
    val ownState: Boolean = false,
    val helper: Helper = Helper.CAREGIVER
)

/** Yksikön perusrutiini: valmis pohja, aika, viikonpäivät ja keille (ohjattu aloitus). */
internal data class RoutineSpec(
    val template: RoutineTemplates.Template,
    val time: String,
    val days: Set<Int>,
    val members: Set<String>
)

/**
 * Asukkaalta toiselle kopiointi ja yhteiset perusasetukset: toisto pois yksikön käyttöönotosta.
 * Jokainen asukas saa oman kopion, jota voi muokata vaikuttamatta muihin.
 */
internal object ResidentCopy {
    /**
     * Asukkaan omat näkymävalinnat ja tuen tason historia eivät kopioidu: ne valitaan
     * asukaskohtaisesti (ohjatun aloituksen vaihe "Kuinka paljon asukas näkee", Tuen taso).
     */
    val PERSONAL_KEYS = setOf(
        "client_timeline", "client_activity_view",
        "support_level", "support_level_previous", "support_level_history"
    )
    private val SETTING_BASES = listOf(ProfileScope.RUN_PREFS, ProfileScope.TIMER_PREFS)

    /** Kopioi asetukset (RUN_PREFS ja TIMER_PREFS) asukkaalta [from] asukkaille [to]. */
    fun copySettings(context: Context, from: String, to: Collection<String>) {
        val app = context.applicationContext
        SETTING_BASES.forEach { base ->
            val source = ProfileScope.prefs(app, base, from).all.filterKeys { it !in PERSONAL_KEYS }
            to.filter { it != from }.forEach { target ->
                val prefs = ProfileScope.prefs(app, base, target)
                val editor = prefs.edit()
                prefs.all.keys.filter { it !in PERSONAL_KEYS }.forEach { editor.remove(it) }
                source.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                editor.commit()
            }
        }
    }

    /** Yhteiset perusasetukset asukkaalle [profileId]. */
    fun applyCommon(context: Context, profileId: String, s: CommonSettings) {
        val app = context.applicationContext
        ProfileScope.prefs(app, ProfileScope.TIMER_PREFS, profileId).edit()
            .putString(TimeDisplayPrefs.KEY, s.timeMode)
            .putInt(TimeDisplayPrefs.WARN_KEY, s.warnMinutes)
            .putInt(MoreTime.MINUTES_KEY, s.moreMinutes)
            .putInt(MoreTime.COUNT_KEY, s.moreCount)
            .commit()
        ProfileScope.prefs(app, ProfileScope.RUN_PREFS, profileId).edit()
            .putBoolean(OwnState.KEY_ENABLED, s.ownState)
            .putString(Helper.KEY, s.helper.key)
            .commit()
    }

    /**
     * Kopioi päiväohjelman asukkaille [to]: kukin saa oman ohjelman (uusi tunnus). Kuvat ja
     * äänitteet ovat yhteisiä tiedostoja; asukkaan poisto ei poista toisen käyttämää tiedostoa.
     * Palauttaa kohde → uuden ohjelman tunnus.
     */
    fun copyProgram(context: Context, program: DayProgram, to: Collection<String>): Map<String, String> =
        to.associateWith { target ->
            val copy = program.copy(id = UUID.randomUUID().toString())
            ProgramStore(context.applicationContext, target).save(copy)
            copy.id
        }

    /** Kopioi viikon kohdan [event] asukkaille [to] (päiväohjelman kohdalla myös ohjelma). */
    fun copyWeekEvent(context: Context, from: String, event: WeekEvent, to: Collection<String>) {
        val app = context.applicationContext
        val program = if (event.kind == EventKind.PROGRAM) ProgramStore(app, from).load().firstOrNull { it.id == event.programId } else null
        to.filter { it != from }.forEach { target ->
            val programId = program?.let { copyProgram(app, it, listOf(target)).getValue(target) } ?: event.programId
            val store = WeekStore(app, target)
            val plan = store.load()
            store.save(plan.copy(events = plan.events + event.copy(id = UUID.randomUUID().toString(), programId = programId)))
        }
    }

    /** Perusrutiinit: jokaiselle valitulle asukkaalle oma päiväohjelma pohjasta ja viikon kohdat. */
    fun applyRoutines(context: Context, routines: List<RoutineSpec>, ids: Map<String, String>, texts: fi.aikapolku.app.cards.Texts) {
        val app = context.applicationContext
        routines.forEach { r ->
            r.members.mapNotNull { ids[it] }.forEach { profileId ->
                val program = ProgramTiming.infer(RoutineTemplates.create(r.template, texts))
                ProgramStore(app, profileId).save(program)
                val store = WeekStore(app, profileId)
                val plan = store.load()
                val events = r.days.sorted().map { d ->
                    WeekEvent(UUID.randomUUID().toString(), d, 0, r.time, EventKind.PROGRAM, programId = program.id)
                }
                store.save(plan.copy(events = plan.events + events))
            }
        }
    }
}
