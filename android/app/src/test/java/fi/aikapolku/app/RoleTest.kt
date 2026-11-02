package fi.aikapolku.app

import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.AppRole
import fi.aikapolku.app.caregiver.CaregiverAccessState
import fi.aikapolku.app.caregiver.RoleSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asiakkaan ja ohjaajan roolit. Puhdas JVM-testi. */
class RoleTest {
    @After
    fun tearDown() = RoleSession.toClient()

    @Test
    fun `asiakkaan roolissa ohjaajan toiminnot eivat ole sallittuja myoskaan ilman lukitusta`() {
        val client = CaregiverAccessState(lockEnabled = false, sessionOpen = false, caregiverRole = false)
        assertTrue(client.isLocked)
        assertFalse(client.allows(AppAction.EDIT_PROGRAMS))
        assertTrue("apu ja tauko aina sallittuja", client.allows(AppAction.HELP) && client.allows(AppAction.BREAK))
        val caregiver = CaregiverAccessState(lockEnabled = true, sessionOpen = true, caregiverRole = true)
        assertTrue(caregiver.allows(AppAction.EDIT_PROGRAMS))
    }

    @Test
    fun `vanha malli sailyy kun roolia ei anneta`() {
        assertTrue(CaregiverAccessState(lockEnabled = false, sessionOpen = false).allows(AppAction.SETTINGS))
        assertFalse(CaregiverAccessState(lockEnabled = true, sessionOpen = false).allows(AppAction.SETTINGS))
    }

    @Test
    fun `katso asiakkaana ja paluu ohjaajaksi`() {
        RoleSession.toCaregiver()
        assertEquals(AppRole.CAREGIVER, RoleSession.role)
        RoleSession.preview()
        assertTrue(RoleSession.previewing)
        RoleSession.endPreview()
        assertFalse(RoleSession.previewing)
        assertEquals(AppRole.CAREGIVER, RoleSession.role)
    }

    @Test
    fun `asiakas ei voi esikatsella`() {
        RoleSession.toClient()
        RoleSession.preview()
        assertFalse(RoleSession.previewing)
    }

    @Test
    fun `testitilassa rooli sailyy kun sovellus menee taustalle`() {
        RoleSession.toCaregiver()
        RoleSession.onBackgroundLock()
        assertEquals("testitila on oletuksena päällä", AppRole.CAREGIVER, RoleSession.role)
    }
}
