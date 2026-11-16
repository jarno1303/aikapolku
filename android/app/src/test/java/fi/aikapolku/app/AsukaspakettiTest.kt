package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.Leave
import fi.aikapolku.app.data.MemberTime
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.link.ResidentPackage
import fi.aikapolku.app.link.ResidentShare
import fi.aikapolku.app.link.SyncState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A5 vaihe 1: asukaspaketti (docs/PAIKAN_LAITE_SUUNNITELMA.md). Paikan laitteella kootaan yhden
 * asukkaan tiedot, ja asukkaan puhelimessa niistä syntyy sama päivä – ilman muiden asukkaiden tietoja.
 * Robolectric vain org.jsonia ja SharedPreferences-tiedostoja varten; ei Composea.
 */
@RunWith(RobolectricTestRunner::class)
class AsukaspakettiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val monday = LocalDate.of(2026, 11, 16)

    private fun card(id: String, day: Int, time: String, name: String, photo: String = "builtin:x") =
        WeekEvent(id, day, 0, time, EventKind.CARD, cardName = name, cardPhoto = photo)

    private val villa = Group("villa", "Villa", kind = GroupKind.UNIT,
        plan = WeekPlan((1..7).flatMap { d -> listOf(card("aamu$d", d, "08:00", "Aamupala"), card("lounas$d", d, "11:00", "Lounas")) }))
    private fun omena(a: String, b: String) = Group("omena", "Omena", listOf(a, b), kind = GroupKind.UNIT, parentId = "villa",
        plan = WeekPlan(listOf(card("sauna", 1, "18:30", "Sauna"))))
    private fun tammi(a: String, b: String) = Group("tammi", "Tammi", listOf(a, b), kind = GroupKind.ACTIVITY,
        sessions = (1..5).map { ActivitySession(it, "09:00", "14:00") },
        memberTimes = listOf(MemberTime(b, start = "10:00", end = "13:00")))

    @Test
    fun `osuus sisaltaa vain asukkaan omat tiedot`() {
        val all = listOf(villa, omena("aino", "eero"), tammi("aino", "eero"))
        val share = ResidentShare.of(
            all,
            changes = mapOf("tammi" to listOf(EventChange("c", monday, "x", ChangeType.CANCEL))),
            absences = mapOf("tammi" to mapOf(monday to setOf("eero"), monday.plusDays(1) to setOf("aino", "eero"))),
            leaves = listOf(Leave("l1", "eero", monday.atTime(10, 0), monday.plusDays(3).atTime(18, 0))),
            memberId = "aino"
        )
        assertEquals(setOf("villa", "omena", "tammi"), share.groups.map { it.id }.toSet())
        val text = ResidentShare.encode(share).toString()
        assertFalse("Toisen asukkaan tunnus ei saa siirtyä", text.contains("eero"))
        assertTrue("Yläyksikkö ilman jäseniä", share.groups.first { it.id == "villa" }.members.isEmpty())
        assertEquals(listOf(ResidentShare.SELF), share.groups.first { it.id == "omena" }.members)
        assertTrue("Toisen oma aika pois", share.groups.first { it.id == "tammi" }.memberTimes.isEmpty())
        assertEquals("Vain asukkaan poissaolot", setOf(monday.plusDays(1)), share.absences["tammi"]?.keys)
        assertTrue("Toisen loma pois", share.leaves.isEmpty())
        assertEquals(1, share.groupChanges["tammi"]?.size)
    }

    @Test
    fun `paketti tuottaa asukkaan puhelimessa saman paivan`() {
        // Paikan laite: kaksi asukasta.
        val aino = ProfileStore.add(context, "Aino")
        val eero = ProfileStore.add(context, "Eero")
        val photo = File(context.filesDir, "photos/omakuva.jpg").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64) { it.toByte() }) }
        WeekStore(context, aino.id).save(WeekPlan(listOf(card("oma", 1, "16:00", "Uinti", photo.absolutePath))))
        WeekStore(context, eero.id).save(WeekPlan(listOf(card("eeron", 1, "16:00", "Eeron oma"))))
        // save ilman päiväsiivousta (upsert siivoaa yli viikon vanhat nykypäivästä laskien).
        ChangeStore(context, aino.id).save(listOf(EventChange("m1", monday, "oma", ChangeType.CANCEL, reason = "Allas kiinni")))
        val groups = GroupStore(context)
        groups.save(listOf(villa, omena(aino.id, eero.id), tammi(aino.id, eero.id)))
        groups.upsertLeave(Leave("l1", eero.id, monday.atTime(10, 0), monday.plusDays(3).atTime(18, 0)), monday.atTime(9, 0))
        val expected = ClientWeek.load(context, aino.id).eventsFor(monday).map { it.time to (it.cardName.ifBlank { it.id }) }

        val built = ResidentPackage.build(context, aino.id)
        assertEquals("Oma kuva mukana", 1, built.mediaSent)

        // Asukkaan puhelin: tyhjennetään kaikki ja puretaan omaan oletusprofiiliin.
        listOf(aino.id, eero.id).forEach { id -> ProfileScope.SCOPED.forEach { b -> ProfileScope.prefs(context, b, id).edit().clear().commit() } }
        groups.save(emptyList())
        groups.replaceShared(setOf("villa", "omena", "tammi"), emptyMap(), emptyMap(), emptyList(), ProfileScope.DEFAULT_ID)
        groups.replaceShared(emptySet(), emptyMap(), emptyMap(), emptyList(), eero.id)
        photo.delete()
        val local = ProfileScope.DEFAULT_ID
        val applied = ResidentPackage.apply(context, built.bytes, placeId = "paikka-1", localId = local)
        assertEquals(0, applied.missing)

        val actual = ClientWeek.load(context, local).eventsFor(monday).map { it.time to (it.cardName.ifBlank { it.id }) }
        assertEquals(expected, actual)
        assertEquals("Muutos tuli mukana", "Allas kiinni", ChangeStore(context, local).load().single().reason)
        val uinti = WeekStore(context, local).load().events.single()
        assertTrue("Kuva purettiin laitteelle", File(uinti.cardPhoto).exists())
        assertTrue("Toisen asukkaan tiedot eivät tulleet", GroupStore(context).leaves().isEmpty())
        assertTrue(GroupStore(context).load().none { g -> g.members.any { it != local } })

        // Toinen haku: kuva on jo laitteella, joten sitä ei lähetetä uudelleen.
        val again = ResidentPackage.build(context, local, ResidentPackage.known(context))
        assertEquals(0, again.mediaSent)
    }

    @Test
    fun `paikan ryhmat korvataan eika muiden paikkojen ryhmia poisteta`() {
        val local = ProfileScope.DEFAULT_ID
        val own = Group("oma", "Laitteen oma", listOf(local))
        GroupStore(context).save(listOf(own))
        val first = ResidentShare(listOf(Group("tammi", "Tammi", listOf(local)), Group("vanha", "Vanha", listOf(local))), emptyMap(), emptyMap(), emptyList())
        SyncState.replacePlaceGroups(context, "paikka-1", first)
        val second = ResidentShare(listOf(Group("tammi", "Tammi uusi", listOf(local))), emptyMap(), emptyMap(), emptyList())
        SyncState.replacePlaceGroups(context, "paikka-1", second)
        val names = GroupStore(context).load().map { it.name }.toSet()
        assertEquals(setOf("Laitteen oma", "Tammi uusi"), names)
    }
}
