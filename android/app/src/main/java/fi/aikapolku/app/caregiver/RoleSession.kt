package fi.aikapolku.app.caregiver

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Kumpi rooli sovelluksessa on: asiakas (oma päivä) vai ohjaaja (muokkaus ja muutokset). */
internal enum class AppRole { CLIENT, CAREGIVER }

/**
 * Asiakkaan ja ohjaajan roolit. Asiakas on oletus. Ohjaajaksi PIN-koodilla, kun lukitus on
 * käytössä; testitilassa (oletus kehitysvaiheessa) vaihto on vapaa yläreunan vaihtimesta.
 * "Katso asiakkaana" näyttää asiakkaan näkymän, ja ohjaajaksi palataan ilman PIN-koodia.
 */
internal object RoleSession {
    private const val PREFS = "aikapolku_roles_v1"
    private const val KEY_TEST = "test_switch"

    private val roleState = mutableStateOf(AppRole.CLIENT)
    private val previewState = mutableStateOf(false)
    private val testState = mutableStateOf(true)

    val role: AppRole get() = roleState.value
    /** Ohjaaja katsoo asiakkaan näkymää; paluu ohjaajaksi ilman PIN-koodia. */
    val previewing: Boolean get() = previewState.value
    /** Testitila: roolin vaihto kaikille ilman PIN-koodia. */
    val testMode: Boolean get() = testState.value

    fun load(context: Context) {
        testState.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TEST, true)
    }

    fun setTestMode(context: Context, on: Boolean) {
        testState.value = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TEST, on).apply()
    }

    fun toCaregiver() {
        roleState.value = AppRole.CAREGIVER
        previewState.value = false
    }

    fun toClient() {
        roleState.value = AppRole.CLIENT
        previewState.value = false
    }

    fun preview() {
        if (roleState.value == AppRole.CAREGIVER) previewState.value = true
    }

    fun endPreview() {
        previewState.value = false
    }

    /** Sovellus siirtyi taustalle (ei omaa valitsinta): takaisin asiakkaaksi, paitsi testitilassa. */
    fun onBackgroundLock() {
        if (!testState.value) toClient()
    }
}
