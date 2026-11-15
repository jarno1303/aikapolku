package fi.aikapolku.app.data

import android.content.Context
import fi.aikapolku.app.R
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tuen taso (palaute 15.11.2026: tuen asteittainen vähentäminen). Taso on valmis yhdistelmä
 * kehotuksia, joita vähennetään asukkaan itsenäistyessä: ennakkovaroitus, Pian vaihtuu -ääni
 * ja seuraavan tehtävän näyttö. Tieto siitä, mitä nyt tehdään ja koko päivä, ei vähene.
 * [key] on tallennusmuodon arvo – älä muuta.
 */
internal enum class SupportLevel(val key: String, val title: Int, val info: Int, val preset: LevelPreset) {
    MUCH("much", R.string.support_level_much, R.string.support_level_much_info, LevelPreset(5, true, RunPreferences.NEXT_CARD)),
    MODERATE("moderate", R.string.support_level_moderate, R.string.support_level_moderate_info, LevelPreset(5, false, RunPreferences.NEXT_CARD)),
    LIGHT("light", R.string.support_level_light, R.string.support_level_light_info, LevelPreset(2, false, RunPreferences.NEXT_TEXT)),
    INDEPENDENT("independent", R.string.support_level_independent, R.string.support_level_independent_info, LevelPreset(0, false, RunPreferences.NEXT_TEXT));

    val next: SupportLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun from(key: String?): SupportLevel? = entries.firstOrNull { it.key == key }
    }
}

/** Tason kehotukset: ennakkovaroitus (min, 0 = pois), Pian vaihtuu -ääni ja seuraavan näyttö. */
internal data class LevelPreset(val warnMinutes: Int, val soonVoice: Boolean, val nextMode: String)

/** Tasoa vaihdettaessa talteen otetut asetukset, joihin "Palaa edelliseen" palauttaa. */
internal data class PromptSettings(val warnMinutes: Int, val soonVoiceMode: String, val nextMode: String)

internal data class LevelChange(val date: LocalDate, val level: SupportLevel)

/** Seurannan perusteella näytettävä huomio ohjaajalle (ei koskaan automaattista muutosta). */
internal enum class LevelHint { NONE, CONSIDER_NEXT, CONSIDER_BACK }

internal object SupportLevels {
    private const val KEY_LEVEL = "support_level"
    private const val KEY_PREVIOUS = "support_level_previous"
    private const val KEY_HISTORY = "support_level_history"
    /** Seurannan vertailujakso (päivää). */
    const val WINDOW_DAYS = 14L

    private fun prefs(context: Context) = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)

    fun current(context: Context): SupportLevel? = SupportLevel.from(prefs(context).getString(KEY_LEVEL, null))

    fun history(context: Context): List<LevelChange> = decodeHistory(prefs(context).getString(KEY_HISTORY, null))

    fun canRevert(context: Context): Boolean = prefs(context).contains(KEY_PREVIOUS)

    fun currentSettings(context: Context): PromptSettings = PromptSettings(
        TimeDisplayPrefs.warnMinutes(context),
        RunPreferences.voiceMode(context, RunPreferences.SOON),
        RunPreferences.nextMode(context)
    )

    /** Ottaa tason käyttöön: nykyiset kehotukset talteen ja tason yhdistelmä tilalle. */
    fun apply(context: Context, level: SupportLevel, today: LocalDate = LocalDate.now()) {
        val before = currentSettings(context)
        val previousLevel = current(context)
        write(context, settingsFor(level.preset, before))
        prefs(context).edit()
            .putString(KEY_LEVEL, level.key)
            .putString(KEY_PREVIOUS, encodePrevious(before, previousLevel))
            .putString(KEY_HISTORY, encodeHistory((history(context) + LevelChange(today, level)).takeLast(20)))
            .apply()
    }

    /** Palauttaa tasoa edeltäneet asetukset ja tason. */
    fun revert(context: Context, today: LocalDate = LocalDate.now()) {
        val (settings, level) = decodePrevious(prefs(context).getString(KEY_PREVIOUS, null)) ?: return
        write(context, settings)
        val e = prefs(context).edit().remove(KEY_PREVIOUS)
        if (level != null) {
            e.putString(KEY_LEVEL, level.key)
            e.putString(KEY_HISTORY, encodeHistory((history(context) + LevelChange(today, level)).takeLast(20)))
        } else {
            // Ennen tasoja: viimeisin tasomerkintä pois historiasta (vertailu alkaa alusta).
            e.remove(KEY_LEVEL)
            e.putString(KEY_HISTORY, encodeHistory(history(context).dropLast(1)))
        }
        e.apply()
    }

    private fun write(context: Context, s: PromptSettings) {
        TimeDisplayPrefs.setWarnMinutes(context, s.warnMinutes)
        RunPreferences.setVoiceMode(context, RunPreferences.SOON, s.soonVoiceMode)
        RunPreferences.setNextMode(context, s.nextMode)
    }

    // --- Puhdas logiikka ---

    /** Tason asetukset; Pian vaihtuu -äänessä säilyy oma äänite, jos sellainen on valittu. */
    fun settingsFor(preset: LevelPreset, before: PromptSettings): PromptSettings = PromptSettings(
        warnMinutes = preset.warnMinutes,
        soonVoiceMode = when {
            !preset.soonVoice -> RunPreferences.AUDIO_SILENT
            before.soonVoiceMode == RunPreferences.AUDIO_SILENT -> RunPreferences.AUDIO_TTS
            else -> before.soonVoiceMode
        },
        nextMode = preset.nextMode
    )

    /**
     * Huomio seurannasta: [recent] ja [previous] = asukkaan pyynnöt (apu, en ymmärrä, tauko,
     * lisäaika) kahdella peräkkäisellä jaksolla, [daysSinceChange] = päiviä tason vaihdosta.
     * Pieniin määriin ei reagoida (satunnaisvaihtelu).
     */
    fun hint(recent: Int, previous: Int, daysSinceChange: Long?, hasNext: Boolean): LevelHint {
        val settled = daysSinceChange == null || daysSinceChange >= WINDOW_DAYS
        return when {
            daysSinceChange != null && daysSinceChange in 1 until WINDOW_DAYS * 2 && recent >= 4 && recent >= previous * 3 / 2 -> LevelHint.CONSIDER_BACK
            hasNext && settled && previous >= 4 && recent * 2 <= previous -> LevelHint.CONSIDER_NEXT
            else -> LevelHint.NONE
        }
    }

    fun encodePrevious(s: PromptSettings, level: SupportLevel?): String =
        JSONObject().put("w", s.warnMinutes).put("v", s.soonVoiceMode).put("n", s.nextMode).put("l", level?.key ?: "").toString()

    fun decodePrevious(text: String?): Pair<PromptSettings, SupportLevel?>? = runCatching {
        val o = JSONObject(text ?: return null)
        PromptSettings(o.getInt("w"), o.getString("v"), o.getString("n")) to SupportLevel.from(o.optString("l"))
    }.getOrNull()

    fun encodeHistory(list: List<LevelChange>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("d", it.date.toString()).put("l", it.level.key)) }
    }.toString()

    fun decodeHistory(text: String?): List<LevelChange> = runCatching {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val level = SupportLevel.from(o.optString("l")) ?: return@mapNotNull null
            LevelChange(LocalDate.parse(o.getString("d")), level)
        }
    }.getOrDefault(emptyList())
}
