package fi.aikapolku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Kahden laitteen siirtotesti (A5): yhteiset apuvälineet. Testit ajetaan yhtä aikaa kahdessa
 * laitteessa (tools/pc/kahden-puhelimen-testi.ps1 -vaihe automaatti):
 *  - [KaksiLaitettaPaikkaDeviceTest] paikan laitteessa (tabletti)
 *  - [KaksiLaitettaAsukasDeviceTest] asukkaan puhelimessa
 * Laitteet eivät tahdistu PC:n kautta, vaan kumpikin odottaa toisen näkyvää tulosta
 * (liitospyyntö, perillä-tila, saapunut viesti). Kaikki tapahtumat lokiin tunnisteella [TAG].
 */
internal object KaksiLaitetta {
    const val TAG = "AikaPolkuKaksi"
    const val PLACE_NAME = "Testipaikka KAKSI"
    const val AINO = "Aino"
    const val EERO = "Eero"
    /** Eeron tapahtuman nimi: ei saa koskaan näkyä Ainon puhelimessa. */
    const val EERO_SECRET = "EeroSalainen"
    const val AINO_EVENT = "kaksi-aino-1"
    const val AINO_EVENT_2 = "kaksi-aino-2"
    const val FIRST_NAME = "Lukeminen KAKSI"
    const val CHANGED_NAME = "Ulkoilu KAKSI"
    const val MESSAGE_1 = "kaksi-viesti-1"
    const val MESSAGE_2 = "kaksi-viesti-2"
    const val TEST_DIR = "kaksi-testi"

    /** Odotusten enimmäisajat. Toinen laite voi käynnistyä hieman myöhemmin. */
    const val WAIT_PEER_MS = 5 * 60_000L
    const val WAIT_STEP_MS = 4 * 60_000L

    fun log(text: String) = Log.i(TAG, text)

    /**
     * Ajetaan vain, kun PC:n skripti antaa argumentin (-e kaksi true): tavallisessa kaikkien
     * laitetestien ajossa nämä ohitetaan (ne odottaisivat toista laitetta).
     */
    fun requested(): Boolean = InstrumentationRegistry.getArguments().getString("kaksi") == "true"

    fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }

    /** Odottaa, kunnes [condition] on tosi, tai aika loppuu. Palauttaa, toteutuiko ehto. */
    fun waitUntil(timeoutMs: Long, stepMs: Long = 2_000L, condition: () -> Boolean): Boolean {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (runCatching(condition).getOrDefault(false)) return true
            Thread.sleep(stepMs)
        }
        return runCatching(condition).getOrDefault(false)
    }

    /** Pieni yksivärinen PNG kuvaksi (eri väri = eri tiiviste). */
    fun photo(context: Context, name: String, color: Int): String {
        val dir = File(context.filesDir, TEST_DIR).apply { mkdirs() }
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            // Yksi pikseli ajokohtaisesti: keskeytyneen ajon jäänteet eivät ole puhelimessa "jo tuttuja".
            setPixel(0, 0, (System.nanoTime() and 0xFFFFFFL).toInt() or 0xFF000000.toInt())
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    val COLOR_1 = Color.rgb(40, 120, 200)
    val COLOR_2 = Color.rgb(200, 120, 40)

    /** Kohtien tulokset: OK tai VIRHE; lopuksi testi kaatuu, jos yksikin VIRHE. */
    class Report(private val device: String) {
        private val lines = mutableListOf<String>()
        val failed get() = lines.count { it.startsWith("VIRHE") }

        fun check(step: String, ok: Boolean, detail: String = "") {
            val line = (if (ok) "OK    " else "VIRHE ") + step + if (detail.isNotBlank()) " ($detail)" else ""
            lines += line
            log("$device: $line")
        }

        fun note(text: String) {
            lines += "      $text"
            log("$device: $text")
        }

        fun text(): String = "$device\n" + lines.joinToString("\n")
    }
}
