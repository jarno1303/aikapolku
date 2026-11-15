package fi.aikapolku.app

import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.MemberTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Yksikkömallin kentät säilyvät tallennuksessa; vanhat ryhmät lukeutuvat ennallaan. */
@RunWith(RobolectricTestRunner::class)
class UnitsStoreTest {
    @Test
    fun `yksikot ja toiminnat sailyvat tallennuksessa`() {
        val groups = listOf(
            Group("villa", "Villa Metsäketo", kind = GroupKind.UNIT),
            Group("omena", "Omena", listOf("a", "b"), kind = GroupKind.UNIT, parentId = "villa"),
            Group(
                "paiva", "Päivätoiminta", listOf("a"), kind = GroupKind.ACTIVITY,
                sessions = listOf(ActivitySession(1, "09:00", "14:00"), ActivitySession(5, "09:00", "12:00")),
                memberTimes = listOf(MemberTime("a", setOf(1), "10:00", "13:00")),
                cardPhoto = "builtin:schoolwork", leaveCard = "Lähtö", returnCard = "Paluu kotiin"
            )
        )
        assertEquals(groups, GroupStore.decode(GroupStore.encode(groups)))
    }

    @Test
    fun `vanha tallennus ilman uusia kenttia on ryhma`() {
        val old = """{"version":1,"groups":[{"id":"g","name":"Tammi","members":["m"],"plan":{"events":[]},"exceptions":[]}]}"""
        val g = GroupStore.decode(old).single()
        assertEquals(GroupKind.GROUP, g.kind)
        assertEquals("", g.parentId)
        assertEquals(emptyList<ActivitySession>(), g.sessions)
    }
}
