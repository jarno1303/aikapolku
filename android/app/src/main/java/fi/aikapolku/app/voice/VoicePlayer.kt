package fi.aikapolku.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File
import java.util.UUID

/** Audio files remain on the device, in application-private storage. */
internal object VoicePlayer {
    private var player: MediaPlayer? = null

    fun stop() {
        val old = player
        player = null
        if (old != null) {
            runCatching { if (old.isPlaying) old.stop() }
            runCatching { old.release() }
        }
    }

    private fun report(
        context: Context,
        message: String
    ): String {
        android.util.Log.w("AikaPolkuVoice", message)

        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).post {
            android.widget.Toast.makeText(
                context.applicationContext,
                message,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        return message
    }

    fun play(context: Context, path: String): String? {
        if (path.isBlank()) {
            return report(
                context,
                "Kortilla ei ole tallennettua puheviestiä."
            )
        }

        val file = File(path)

        val valid = runCatching {
            val base = context.applicationContext
                .filesDir.canonicalFile

            file.isFile &&
                file.canonicalFile.toPath()
                    .startsWith(base.toPath())
        }.getOrDefault(false)

        if (!valid) {
            return report(
                context,
                "Puheviestin äänitiedostoa ei löydy."
            )
        }

        stop()

        val next = MediaPlayer()

        return try {
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            next.setDataSource(file.absolutePath)

            next.setOnCompletionListener { mp ->
                if (player === mp) {
                    stop()
                } else {
                    runCatching { mp.release() }
                }
            }

            next.setOnErrorListener { mp, what, extra ->
                android.util.Log.e(
                    "AikaPolkuVoice",
                    "MediaPlayer error: $what / $extra"
                )

                report(
                    context,
                    "Puheviestin toistossa tapahtui virhe."
                )

                if (player === mp) {
                    stop()
                } else {
                    runCatching { mp.release() }
                }

                true
            }

            next.prepare()
            player = next
            next.start()

            null
        } catch (e: Exception) {
            if (player === next) {
                player = null
            }

            runCatching { next.release() }

            android.util.Log.e(
                "AikaPolkuVoice",
                "Puheviestin käynnistys epäonnistui",
                e
            )

            report(
                context,
                "Puheviestin toisto epäonnistui."
            )
        }
    }
}

internal class CardVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start() {
        cancel()
        val folder = File(context.filesDir, "voice_messages")
        if (!folder.exists() && !folder.mkdirs()) error("Äänikansiota ei voitu luoda")
        val file = File(folder, "voice_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(22050)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
        } catch (e: Exception) {
            runCatching { r.reset() }
            r.release()
            file.delete()
            throw e
        }
    }

    fun finish(): String {
        val r = recorder ?: error("Tallennus ei ole käynnissä")
        val file = output ?: error("Tallennustiedosto puuttuu")
        recorder = null
        output = null
        try {
            r.stop()
            if (file.length() <= 0L) error("Äänitallenne on tyhjä")
            return file.absolutePath
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            r.release()
        }
    }

    fun cancel() {
        val r = recorder
        recorder = null
        if (r != null) {
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        output?.delete()
        output = null
    }
}
