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
