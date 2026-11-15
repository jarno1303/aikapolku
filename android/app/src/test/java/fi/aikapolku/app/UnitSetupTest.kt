package fi.aikapolku.app

import fi.aikapolku.app.data.ActivitySpec
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.SubUnitSpec
import fi.aikapolku.app.data.UnitSetup
import fi.aikapolku.app.data.UnitSpec
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ohjattu aloitus (V4): rakentaa yksikön, alayksiköt, ruokailut ja toiminnat oikein. Puhdas JVM-testi. */
class UnitSetupTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private var n = 0
    private fun id() = "id${n++}"

    private val spec = UnitSpec(
        name = "Villa Metsäketo",
        subUnits = listOf(SubUnitSpec("Omena", listOf("o1", "o2")), SubUnitSpec("Päärynä", listOf("p1"))),
        members = emptyList(),
        meals = UnitSetup.defaultMeals { it },
        activities = listOf(ActivitySpec("Päivätoiminta", "builtin:schoolwork", (1..5).toSet(), "09:00", "14:00", setOf("o1", "p1")))
    )

    @Test
    fun `yksikko alayksikot ja toiminta`() {
        val groups = UnitSetup.build(spec, ::id)
        val top = groups.first()
        assertEquals(GroupKind.UNIT, top.kind)
        assertTrue(top.members.isEmpty())
        assertEquals(5 * 5 + 5 * 2, top.plan.events.size)
        val subs = groups.filter { it.parentId == top.id }
        assertEquals(listOf("Omena", "Päärynä"), subs.map { it.name })
        val act = groups.single { it.kind == GroupKind.ACTIVITY }
        assertEquals(5, act.sessions.size)
    }

    @Test
    fun `rakennettu yksikko toimii asukkaan paivassa`() {
        val groups = UnitSetup.build(spec, ::id)
        fun day(m: String) = ClientDay(WeekPlan(), Units.groupsFor(groups, m), m).eventsFor(monday).map { it.cardName }
        assertTrue("lunch_meal" in day("o2"))
        assertFalse("lunch_meal" in day("o1"))
        assertTrue("Päivätoiminta" in day("p1"))
        // Viikonloppuna aamupala klo 9.
        val sat = ClientDay(WeekPlan(), Units.groupsFor(groups, "o1"), "o1").eventsFor(monday.plusDays(5))
        assertEquals("09:00", sat.first { it.cardName == "breakfast" }.time)
    }

    @Test
    fun `ilman alayksikoita asukkaat suoraan yksikossa`() {
        val groups = UnitSetup.build(spec.copy(subUnits = emptyList(), members = listOf("a", "b")), ::id)
        assertEquals(listOf("a", "b"), groups.first().members)
    }
}
