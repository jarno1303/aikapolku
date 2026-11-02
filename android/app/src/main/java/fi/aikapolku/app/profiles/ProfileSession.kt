package fi.aikapolku.app.profiles

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.week.EventReminders

/**
 * Profiilien muutokset käyttöliittymälle. [revision] kasvaa jokaisesta muutoksesta, jolloin
 * näkymät lukevat profiilit uudelleen; profiilin vaihtuessa koko sovelluksen tila luodaan
 * uudelleen (MainActivity: key(profiili)).
 */
internal object ProfileSession {
    private val revisionState = mutableIntStateOf(0)
    val revision: Int get() = revisionState.intValue

    fun changed() {
        revisionState.intValue++
    }

    // Asetuksista vaihdettaessa sovellus palaa vaihdon jälkeen Asetuksiin (ei etusivulle).
    @Volatile private var reopenSettings = false

    /** Kertakäyttöinen: true, jos edellinen vaihto tehtiin Asetuksista. */
    fun takeReopenSettings(): Boolean = reopenSettings.also { reopenSettings = false }

    /** Vaihto Asetukset-näkymästä: näkymä avautuu uudelleen uudelle asiakkaalle. */
    fun switchFromSettings(context: Context, id: String) {
        if (id == ProfileStore.currentId(context)) return
        reopenSettings = true
        switchTo(context, id)
    }

    /** Poisto Asetuksista: jos valittu asiakas poistetaan, Asetukset avautuu seuraavalle. */
    fun deleteFromSettings(context: Context, id: String): Boolean {
        if (id == ProfileStore.currentId(context)) reopenSettings = true
        return delete(context, id).also { if (!it) reopenSettings = false }
    }

    fun switchTo(context: Context, id: String) {
        if (id == ProfileStore.currentId(context)) return
        stopTimers(context)
        ProfileStore.switchTo(context, id)
        changed()
    }

    fun delete(context: Context, id: String): Boolean {
        if (id == ProfileStore.currentId(context)) stopTimers(context)
        val deleted = ProfileStore.delete(context, id)
        // Poistetun profiilin ilmoitukset eivät saa enää tulla.
        EventReminders.reschedule(context.applicationContext)
        changed()
        return deleted
    }

    /** Käynnissä olevat ajastimet pysäytetään, jotta edellisen profiilin hälytys ei soi uudessa. */
    private fun stopTimers(context: Context) {
        listOf("timer", "program").forEach { owner ->
            if (BackgroundTimer.running(context, owner)) {
                BackgroundTimer.stop(context, owner, BackgroundTimer.remaining(context, owner, 0L))
            }
        }
    }
}
