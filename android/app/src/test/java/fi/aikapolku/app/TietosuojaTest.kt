package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.PrivacyPrefs
import fi.aikapolku.app.link.LinkStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tietosuojan puutelistan korjaukset (T4, T5): oletukset ja paikan laitteen vastaanotto. */
@RunWith(RobolectricTestRunner::class)
class TietosuojaTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `ohjaajan nakymat piilossa oletuksena`() {
        assertTrue(PrivacyPrefs.secureScreens(context))
        PrivacyPrefs.setSecureScreens(context, false)
        assertFalse(PrivacyPrefs.secureScreens(context))
    }

    @Test
    fun `paikan laite on aina myos vastaanottaja`() {
        val store = LinkStore(context)
        assertFalse(store.placeEnabled)
        store.placeEnabled = true
        assertTrue(store.receiverEnabled)
    }
}
