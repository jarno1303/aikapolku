package fi.aikapolku.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Äänittää lyhyen viestin sovelluksen omaan kansioon (voice_messages).
 * Yhdistää aiemmat kaksi lähes identtistä äänittäjää (korttien äänet ja
 * apu-/taukoviestit); [filePrefix] erottaa tiedostot toisistaan.
 */
internal class VoiceRecorder(private val context: Context, private val filePrefix: String) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start() {
        cancel()
        val dir = File(context.filesDir, "voice_messages")
        check(dir.exists() || dir.mkdirs()) { "Tallennuskansiota ei voitu luoda" }
        val output = File(dir, "${filePrefix}_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(22050)
            next.setAudioEncodingBitRate(64000)
            next.setOutputFile(output.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            file = output
        } catch (e: Exception) {
            runCatching { next.reset() }
            runCatching { next.release() }
            output.delete()
            throw e
        }
    }

    /** Lopettaa äänityksen ja palauttaa tiedoston polun. */
    fun finish(): String {
        val current = recorder ?: error("Äänitys ei ole käynnissä")
        val output = file ?: error("Tallennetiedosto puuttuu")
        recorder = null
        file = null
        return try {
            current.stop()
            check(output.length() > 0L) { "Äänitallenne on tyhjä" }
            output.absolutePath
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            runCatching { current.release() }
        }
    }

    fun cancel() {
        recorder?.let { current ->
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }
}
