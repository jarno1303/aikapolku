package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.MemberException
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ryhmien tallennus, poissaolot ja asiakkaan poisto. Ei Composen tilaa (ProfileStore suoraan,
 * ei ProfileSessionia), jotta muut Compose-testit eivät jumitu.
 */
@RunWith(RobolectricTestRunner::class)
class GroupStoreTest {
    private lateinit var context: Context
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun group(members: List<String>) = Group(
        id = "tammi",
        name = "Päivätoiminta Tammi",
        members = members,
        plan = WeekPlan(listOf(WeekEvent("lounas", 1, 0, "11:00", EventKind.CARD, cardName = "Lounas", notify = true, notifyBefore = 5))),
        exceptions = listOf(MemberException("lounas", members.first(), ExceptionKind.REPLACE, "Eväät", "builtin:snack"))
    )

    @Test
    fun `ryhma tallentuu ja latautuu ennallaan`() {
        val store = GroupStore(context)
        val g = group(listOf("a", "b"))
        store.save(listOf(g))
        assertEquals(listOf(g), GroupStore(context).load())
        assertTrue(GroupStore.decode("rikki").isEmpty())
    }

    @Test
    fun `poissaolo merkitaan ja palautetaan, vanhat siivotaan`() {
        val store = GroupStore(context)
        store.setAbsent("tammi", "a", monday, true, today = monday)
        store.setAbsent("tammi", "b", monday, true, today = monday)
        assertTrue(store.isAbsent("tammi", "a", monday))
        store.setAbsent("tammi", "a", monday, false, today = monday)
        assertFalse(store.isAbsent("tammi", "a", monday))
        assertTrue(store.isAbsent("tammi", "b", monday))
        // Kahden viikon päästä merkitty poissaolo siivoaa vanhat pois.
        store.setAbsent("tammi", "c", monday.plusDays(14), true, today = monday.plusDays(14))
        assertFalse(store.isAbsent("tammi", "b", monday))
    }

    @Test
    fun `asiakkaan paiva yhdistaa ryhman ja omat tallennetuista tiedoista`() {
        val me = ProfileStore.currentId(context)
        WeekStore(context, me).save(WeekPlan(listOf(WeekEvent("oma", 1, 0, "12:00", EventKind.CARD, cardName = "Lääkkeet"))))
        GroupStore(context).save(listOf(group(listOf("muu", me))))
        assertEquals(listOf("Lounas", "Lääkkeet"), ClientWeek.load(context).eventsFor(monday).map { it.cardName })
        GroupStore(context).setAbsent("tammi", me, monday, true, today = monday)
        assertEquals(listOf("Lääkkeet"), ClientWeek.load(context).eventsFor(monday).map { it.cardName })
    }

    @Test
    fun `asiakkaan poisto poistaa hanet ryhmista, poikkeuksista ja poissaoloista`() {
        val other = ProfileStore.add(context, "Mikko")
        val store = GroupStore(context)
        store.save(listOf(group(listOf(other.id, "anna"))))
        store.setAbsent("tammi", other.id, monday, true, today = monday)
        assertTrue(ProfileStore.delete(context, other.id))
        val g = GroupStore(context).load().single()
        assertEquals(listOf("anna"), g.members)
        assertTrue(g.exceptions.isEmpty())
        assertFalse(GroupStore(context).isAbsent("tammi", other.id, monday))
    }
}
