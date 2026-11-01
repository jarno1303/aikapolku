package fi.aikapolku.app.timer

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal suspend fun playCompletionSound(mode: String) {
    if (mode != "short" && mode != "clear") return

    withContext(Dispatchers.Default) {
        val generator = try {
            ToneGenerator(
                AudioManager.STREAM_MUSIC,
                ToneGenerator.MAX_VOLUME
            )
        } catch (e: Exception) {
            Log.e("AikaPolkuAudio", "Aanen alustus epaonnistui", e)
            null
        } ?: return@withContext

        try {
            val repeats = if (mode == "short") 1 else 3
            val tone = if (mode == "short") {
                ToneGenerator.TONE_PROP_PROMPT
            } else {
                ToneGenerator.TONE_DTMF_5
            }

            repeat(repeats) {
                val success = generator.startTone(
                    tone,
                    if (mode == "short") 250 else 350
                )

                if (!success) {
                    Log.e(
                        "AikaPolkuAudio",
                        "Aanimerkin kaynnistys epaonnistui"
                    )
                }

                delay(
                    if (mode == "short") 350L else 480L
                )
            }
        } finally {
            generator.stopTone()
            generator.release()
        }
    }
}
