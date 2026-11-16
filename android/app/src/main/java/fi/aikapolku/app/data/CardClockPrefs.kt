package fi.aikapolku.app.data

import android.content.Context

/** Kello korteissa: pieni kellotaulu näyttää toiminnan alkamisajan (asukaskohtainen, oletus pois). */
internal enum class CardClockMode(val key: String) {
    OFF("off"),
    ANALOG("analog"),
    /** Kellotaulu ja numeroina (esim. 12.00). */
    BOTH("both");

    companion object {
        fun from(key: String?): CardClockMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

internal object CardClockPrefs {
    const val KEY = "client_card_clock"

    fun mode(context: Context, profileId: String = ProfileStore.currentId(context)): CardClockMode =
        CardClockMode.from(ProfileScope.prefs(context, ProfileScope.RUN_PREFS, profileId).getString(KEY, null))

    fun setMode(context: Context, mode: CardClockMode) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putString(KEY, mode.key).apply()
    }

    /** Kellonaika minuutteina vuorokauden alusta ("8:05", "08.05"); null, jos ei kellonaikaa. */
    fun minutes(time: String?): Int? {
        val parts = time?.trim()?.split(':', '.')?.takeIf { it.size == 2 } ?: return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Viisarien kulmat asteina kello 12:sta myötäpäivään: tunti- ja minuuttiviisari. */
    fun handAngles(minutesOfDay: Int): Pair<Float, Float> {
        val h = (minutesOfDay / 60) % 12
        val m = minutesOfDay % 60
        return ((h + m / 60f) * 30f) to (m * 6f)
    }
}
