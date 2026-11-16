package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.backup.BackupException
import fi.aikapolku.app.backup.DeviceBackup
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.link.LinkStore
import fi.aikapolku.app.link.Peer
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Koko laitteen varmuuskopio ja palautus (paikan laite): tunnukset, yksiköt, kuvat ja liitokset säilyvät. */
@RunWith(RobolectricTestRunner::class)
class LaitteenVarmuuskopioTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `varmuuskopio palauttaa kaiken ja vaatii oikean salasanan`() {
        val aino = ProfileStore.add(context, "Aino")
        GroupStore(context).save(listOf(Group("omena", "Omena", listOf(aino.id))))
        val photo = File(context.filesDir, "photos/kuva.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        LinkStore(context).addPeer(Peer("puhelin", "Ainon puhelin", ByteArray(32) { 7 }, receiver = false, resident = aino.id))

        val bytes = DeviceBackup.create(context, "salasana1")
        try {
            DeviceBackup.read(bytes, "väärä-salasana")
            fail("Väärä salasana ei saa avata")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.WRONG_PASSWORD, e.reason)
        }

        // Laite "hajoaa": tiedot pois.
        GroupStore(context).save(emptyList())
        LinkStore(context).removePeer("puhelin", receiver = false)
        photo.delete()

        DeviceBackup.restore(context, DeviceBackup.read(bytes, "salasana1"))
        assertTrue(ProfileStore.list(context).any { it.id == aino.id && it.name == "Aino" })
        assertEquals(listOf(aino.id), GroupStore(context).load().single().members)
        assertArrayEquals(byteArrayOf(1, 2, 3), photo.readBytes())
        val peer = LinkStore(context).peers().single()
        assertEquals(aino.id, peer.resident)
        assertArrayEquals(ByteArray(32) { 7 }, peer.key)
    }
}
