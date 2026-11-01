package fi.aikapolku.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File

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

