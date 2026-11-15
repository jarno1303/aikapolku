package fi.aikapolku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.SoonVoice
import fi.aikapolku.app.data.SoonVoicePlan
import fi.aikapolku.app.data.normalizePhoto
import fi.aikapolku.app.timer.ClockDial
import fi.aikapolku.app.timer.DialText
import fi.aikapolku.app.voice.SoonVoiceEffect
import fi.aikapolku.app.voice.SoonVoiceRuntime
import fi.aikapolku.app.voice.VoicePlayer
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Puhelintestit: ennakkovaroituksen ääni, Ota kuva -kuvien käsittely ja kellotaulun laskenta.
 * Väliaikainen asiakas poistetaan lopuksi; laitteen yleinen äänite palautetaan ennalleen.
 */

private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

private object AaniProfile {
    var original = ""
    var id = ""
    var generalBefore = ""

    fun create() {
        original = ProfileStore.currentId(ctx)
        ProfileStore.list(ctx).filter { it.name == NAME }.forEach { ProfileStore.delete(ctx, it.id) }
        id = ProfileStore.add(ctx, NAME).id
        ProfileStore.switchTo(ctx, id)
        RoleSession.load(ctx)
        generalBefore = SoonVoice.generalRecording(ctx)
    }

    fun remove() {
        VoicePlayer.stop()
        SoonVoice.setGeneralRecording(ctx, generalBefore)
        ProfileStore.switchTo(ctx, original)
        ProfileStore.delete(ctx, id)
    }

    const val NAME = "Automaattitesti ääni"
}

/** 1 sekunnin äänimerkki WAV-tiedostona sovelluksen omaan kansioon (kuten äänitteet). */
private fun toneFile(name: String, hz: Double): String {
    val dir = File(ctx.filesDir, "voice_messages").apply { mkdirs() }
    val file = File(dir, name)
    val rate = 22050
    val samples = rate
    val data = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val v = (sin(2 * PI * hz * i / rate) * 6000).toInt()
        data[2 * i] = (v and 0xff).toByte()
        data[2 * i + 1] = ((v shr 8) and 0xff).toByte()
    }
    RandomAccessFile(file, "rw").use { f ->
        f.setLength(0)
        fun int(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
        fun short(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
        f.write("RIFF".toByteArray()); int(36 + data.size); f.write("WAVE".toByteArray())
        f.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        f.write("data".toByteArray()); int(data.size); f.write(data)
    }
    return file.absolutePath
}

/**
 * Ennakkovaroituksen ääni puhelimessa: puheääni ja oma äänite, asiakkaan oma korvaa yleisen,
 * ääni käsitellään kerran tehtävää kohden eikä piilotettu seuraava paljastu. Jos puhelin on
 * äänettömällä tai Älä häiritse -tilassa, mitään ei soiteta (sekin tarkistetaan).
 */
@RunWith(AndroidJUnit4::class)
class EnnakkoaaniDeviceTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() = AaniProfile.create()

        @AfterClass
        @JvmStatic
        fun tearDown() = AaniProfile.remove()
    }

    @get:Rule
    val compose = createComposeRule()

    private var soon by mutableStateOf(false)
    private var key by mutableStateOf("a")
    private var nextName by mutableStateOf<String?>("Lounas")

    private fun show() {
        SoonVoiceRuntime.lastPlayedKey = null
        compose.setContent {
            Column(Modifier.padding(24.dp)) {
                Text("Ennakkovaroituksen ääni – automaattitesti")
                Text(if (soon) "Pian vaihtuu ($key)" else "Odottaa ($key)")
                SoonVoiceEffect(soon = soon, key = key, nextName = nextName)
            }
        }
    }

    private fun trigger(newKey: String) {
        soon = false
        compose.waitForIdle()
        key = newKey
        soon = true
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private val muted get() = SoonVoiceRuntime.muted(ctx)

    @Test
    fun puheaani_kerran_tehtavaa_kohden_ja_piilotettu_seuraava_ei_paljastu() {
        RunPreferences.setVoiceMode(ctx, RunPreferences.SOON, RunPreferences.AUDIO_TTS)
        show()
        val before = SoonVoiceRuntime.handled
        trigger("tehtava-1")
        assertEquals("Varoitus käsitellään kerran", before + 1, SoonVoiceRuntime.handled)
        val soonText = ctx.getString(R.string.transition_soon_title)
        val expected = if (muted) SoonVoicePlan.None
        else SoonVoicePlan.Speak("$soonText. ${ctx.getString(R.string.run_next_line, "Lounas")}.")
        assertEquals(expected, SoonVoiceRuntime.lastPlan)

        // Sama tehtävä uudelleen (esim. tauon tai lisäajan jälkeen): ei toistu.
        soon = false; compose.waitForIdle(); soon = true; compose.waitForIdle()
        assertEquals("Ei soi toista kertaa samassa tehtävässä", before + 1, SoonVoiceRuntime.handled)

        // Piilotettu seuraava: vain "Pian vaihtuu."
        nextName = null
        trigger("tehtava-2")
        assertEquals(if (muted) SoonVoicePlan.None else SoonVoicePlan.Speak("$soonText."), SoonVoiceRuntime.lastPlan)
        nextName = "Lounas"
        Thread.sleep(2_500)
    }

    @Test
    fun yleinen_aanite_soi_ja_asiakkaan_oma_korvaa_sen() {
        val general = toneFile("soon_test_general.wav", 440.0)
        val own = toneFile("soon_test_own.wav", 660.0)
        try {
            SoonVoice.setGeneralRecording(ctx, general)
            SoonVoice.setClientRecording(ctx, "")
            RunPreferences.setVoiceMode(ctx, RunPreferences.SOON, RunPreferences.AUDIO_RECORDED)
            show()

            trigger("aanite-1")
            assertEquals(if (muted) SoonVoicePlan.None else SoonVoicePlan.Play(listOf(general)), SoonVoiceRuntime.lastPlan)
            if (!muted) assertTrue("Yleinen äänite soi", waitPlaying())
            Thread.sleep(1_500)

            SoonVoice.setClientRecording(ctx, own)
            trigger("aanite-2")
            assertEquals(if (muted) SoonVoicePlan.None else SoonVoicePlan.Play(listOf(own)), SoonVoiceRuntime.lastPlan)
            if (!muted) assertTrue("Asiakkaan oma äänite soi", waitPlaying())
            Thread.sleep(1_500)

            // Oletus (ei ääntä): ei mitään.
            RunPreferences.setVoiceMode(ctx, RunPreferences.SOON, RunPreferences.AUDIO_SILENT)
            trigger("aanite-3")
            assertEquals(SoonVoicePlan.None, SoonVoiceRuntime.lastPlan)
        } finally {
            VoicePlayer.stop()
            SoonVoice.setClientRecording(ctx, "")
            File(general).delete()
            File(own).delete()
        }
    }

    private fun waitPlaying(): Boolean {
        repeat(20) {
            if (VoicePlayer.isPlaying) return true
            Thread.sleep(100)
        }
        return false
    }
}

/**
 * Ota kuva: kameran pystykuva käännetään oikein päin ja suuri kuva pienennetään; kameran
 * väliaikaistiedoston jakopolku on määritelty. Kellotaulu: viimeisten sekuntien laskenta mahtuu
 * pieneen tauluun puhelimen omalla fonttikoolla.
 */
@RunWith(AndroidJUnit4::class)
class KuvaJaKelloDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun jpeg(name: String, w: Int, h: Int): File {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 150, 140)) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    private fun size(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun kameran_pystykuva_kaantyy_ja_suuri_kuva_pienenee() {
        val portrait = jpeg("exif_test.jpg", 400, 200)
        ExifInterface(portrait.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        normalizePhoto(portrait)
        assertEquals("Pystykuva oikein päin", 200 to 400, size(portrait))

        val big = jpeg("big_test.jpg", 3000, 2000)
        normalizePhoto(big)
        val (w, h) = size(big)
        assertTrue("Suuri kuva pienennetty ($w x $h)", maxOf(w, h) <= 2048 && w > h)

        // Kameran väliaikainen tiedosto voidaan antaa kamerasovellukselle (file_paths.xml).
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", portrait)
        assertTrue(uri.toString().startsWith("content://"))
        portrait.delete()
        big.delete()
    }

    @Test
    fun viimeisten_sekuntien_laskenta_mahtuu_pieneen_tauluun() {
        listOf(150.dp, 174.dp, 212.dp, 300.dp).forEach { dial ->
            compose.setContentOnce(dial)
            compose.waitForIdle()
            val node = compose.onNodeWithTag(DialText.LAST_TEN_TAG, useUnmergedTree = true).fetchSemanticsNode()
            val d = node.layoutInfo.density.density
            val w = node.size.width / d
            val h = node.size.height / d
            val inner = DialText.innerDiameter(dial).value
            val corner = sqrt((w / 2) * (w / 2) + (h / 2) * (h / 2))
            assertTrue("Taulu ${dial.value} dp: ${w}x$h dp ei mahdu keskustaan $inner dp", corner <= inner / 2 + 1f)
        }
        Thread.sleep(1_500)
    }

    private var dialSize by mutableStateOf(150.dp)
    private var contentSet = false

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setContentOnce(dial: androidx.compose.ui.unit.Dp) {
        dialSize = dial
        if (!contentSet) {
            contentSet = true
            setContent {
                Column(Modifier.padding(24.dp)) {
                    Text("Kellotaulu – viimeiset sekunnit")
                    ClockDial(remaining = 9_000L, seconds = 9, lastTen = true, onAdjustMinutes = {}, modifier = Modifier.size(dialSize))
                }
            }
        }
    }
}
