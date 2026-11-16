package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.CommonSettings
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Helper
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.ResidentCopy
import fi.aikapolku.app.data.RoutineSpec
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Yksikön käyttöönotto ilman toistoa: yhteiset asetukset, perusrutiinit ja kopiointi asukkaalta toiselle. */
@RunWith(RobolectricTestRunner::class)
class KopiointiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `asetusten kopiointi sailyttaa kunkin oman nakyman`() {
        val aino = ProfileStore.add(context, "Aino").id
        val eero = ProfileStore.add(context, "Eero").id
        ResidentCopy.applyCommon(context, aino, CommonSettings(timeMode = TimeDisplayPrefs.CALM, warnMinutes = 10, helper = Helper.RELATIVE))
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS, aino).edit().putString("client_timeline", "full").commit()
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS, eero).edit().putString("client_timeline", "none").commit()

        ResidentCopy.copySettings(context, aino, listOf(eero))
        val timer = ProfileScope.prefs(context, ProfileScope.TIMER_PREFS, eero)
        assertEquals(TimeDisplayPrefs.CALM, timer.getString(TimeDisplayPrefs.KEY, null))
        assertEquals(10, timer.getInt(TimeDisplayPrefs.WARN_KEY, 0))
        val run = ProfileScope.prefs(context, ProfileScope.RUN_PREFS, eero)
        assertEquals("laheinen", run.getString(Helper.KEY, null))
        assertEquals("Oma näkymävalinta säilyy", "none", run.getString("client_timeline", null))
    }

    @Test
    fun `perusrutiini tulee jokaiselle omana ohjelmana viikkoon`() {
        val aino = ProfileStore.add(context, "Aino").id
        val eero = ProfileStore.add(context, "Eero").id
        val morning = RoutineTemplates.all.first { it.nameRes == R.string.template_morning }
        ResidentCopy.applyRoutines(
            context,
            listOf(RoutineSpec(morning, "07:30", setOf(1, 2, 3, 4, 5), setOf("a", "e"))),
            mapOf("a" to aino, "e" to eero)
        ) { context.getString(it) }
        val ainoProgram = ProgramStore(context, aino).load().single()
        val eeroProgram = ProgramStore(context, eero).load().single()
        assertNotEquals("Kummallakin oma ohjelma", ainoProgram.id, eeroProgram.id)
        val week = WeekStore(context, aino).load().events
        assertEquals(5, week.size)
        assertEquals(setOf(ainoProgram.id), week.map { it.programId }.toSet())
        assertEquals("07:30", week.first().time)
    }

    @Test
    fun `viikon kohdan kopiointi kopioi myos paivaohjelman`() {
        val aino = ProfileStore.add(context, "Aino").id
        val eero = ProfileStore.add(context, "Eero").id
        val morning = RoutineTemplates.all.first { it.nameRes == R.string.template_morning }
        ResidentCopy.applyRoutines(context, listOf(RoutineSpec(morning, "07:30", setOf(1), setOf("a"))), mapOf("a" to aino)) { context.getString(it) }
        val event = WeekStore(context, aino).load().events.single()
        ResidentCopy.copyWeekEvent(context, aino, event, listOf(eero))
        val copied = WeekStore(context, eero).load().events.single()
        assertEquals(EventKind.PROGRAM, copied.kind)
        assertEquals(ProgramStore(context, eero).load().single().id, copied.programId)
        assertNotEquals(event.programId, copied.programId)
    }
}
