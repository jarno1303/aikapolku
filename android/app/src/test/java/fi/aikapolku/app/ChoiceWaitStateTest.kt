package fi.aikapolku.app

import fi.aikapolku.app.data.ChoiceAlt
import fi.aikapolku.app.data.OwnState
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.StateEntry
import fi.aikapolku.app.data.StateLevel
import fi.aikapolku.app.data.WaitState
import fi.aikapolku.app.data.Waiting
import fi.aikapolku.app.data.chosen
import fi.aikapolku.app.data.stepFromJson
import fi.aikapolku.app.data.stepToJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Valinta, odotetaan ja oma tila (15.11.2026). */
@RunWith(RobolectricTestRunner::class)
class ChoiceWaitStateTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val step = ProgramStep(
        id = "s1", name = "Ulkoilu", photo = "builtin:outdoors", description = "Kenkä jalkaan",
        subSteps = listOf("Kengät", "Takki"), alternative = ChoiceAlt("Musiikki", "builtin:music", "/aani.m4a")
    )

    @Test
    fun `valinta korvaa vaiheen tai poistaa vaihtoehdon`() {
        val alt = step.chosen(useAlternative = true)
        assertEquals("Musiikki", alt.name)
        assertEquals("builtin:music", alt.photo)
        assertEquals("/aani.m4a", alt.voicePath)
        assertTrue("osavaiheet kuuluvat alkuperäiselle", alt.subSteps.isEmpty())
        assertNull(alt.alternative)
        val same = step.chosen(useAlternative = false)
        assertEquals("Ulkoilu", same.name)
        assertEquals(2, same.subSteps.size)
        assertNull(same.alternative)
    }

    @Test
    fun `vaihtoehto tallentuu ohjelmaan`() {
        val back = stepFromJson(stepToJson(step))
        assertEquals(step.alternative, back.alternative)
        assertNull(stepFromJson(stepToJson(step.copy(alternative = null))).alternative)
    }

    @Test
    fun `odotuksen kesto ja tallennus`() {
        val w = WaitState("Taksi", "builtin:leaving", startedAt = 0L, minutes = 10)
        assertEquals(1f, w.fractionLeft(0L)!!, 0.001f)
        assertEquals(0.5f, w.fractionLeft(5 * 60_000L)!!, 0.001f)
        assertEquals(0f, w.fractionLeft(20 * 60_000L)!!, 0.001f)
        assertNull(w.copy(minutes = 0).fractionLeft(0L))
        assertEquals(w, Waiting.decode(Waiting.encode(w)))
    }

    @Test
    fun `oma tila sailyy vain eilisen ja taman paivan`() {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val list = listOf(StateEntry(StateLevel.LOW, now - 3 * day), StateEntry(StateLevel.GOOD, now - day), StateEntry(StateLevel.OK, now))
        assertEquals(2, OwnState.prune(list, now).size)
        assertEquals(list, OwnState.decode(OwnState.encode(list)))
    }
}
