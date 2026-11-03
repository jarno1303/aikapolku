package fi.aikapolku.app.week

import androidx.compose.runtime.mutableIntStateOf

/**
 * Ryhmien muutoslaskuri: kun poissaolo, ryhmän muutos tai runko muuttuu, Tänään-lista ja
 * NYT-kortti lukevat asiakkaan päivän uudelleen.
 */
internal object GroupSession {
    private val state = mutableIntStateOf(0)
    val revision: Int get() = state.intValue

    fun changed() {
        state.intValue++
    }
}
