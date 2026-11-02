package fi.aikapolku.app.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Asetusten muutoslaskuri: näkymät, jotka lukevat asetuksia (ajastin, päiväohjelman ajo),
 * lukevat ne uudelleen, kun Asetukset-näkymässä muutetaan jotain. Profiilin vaihto
 * huomioidaan erikseen (ProfileSession.revision).
 */
internal object SettingsRevision {
    var value by mutableIntStateOf(0)
        private set

    fun bump() {
        value += 1
    }
}
