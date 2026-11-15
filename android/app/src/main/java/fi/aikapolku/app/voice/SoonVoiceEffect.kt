package fi.aikapolku.app.voice

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.R
import fi.aikapolku.app.data.SoonVoice
import fi.aikapolku.app.data.SoonVoicePlan

internal object SoonVoiceRuntime {
    /**
     * Viimeksi soitetun varoituksen tunniste. Muistissa sovelluksen ajan, jotta ääni soi kerran
     * tehtävää kohden, vaikka asiakas käy välillä tauko- tai apuviestissä (näkymä luodaan uudelleen)
     * tai saa lisäaikaa.
     */
    var lastPlayedKey: String? = null

    /** Äänetön, värinä tai Älä häiritse: ääntä ei soiteta. */
    fun muted(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}

/**
 * Soittaa ennakkovaroituksen äänen kerran, kun [soon] muuttuu todeksi. [key] yksilöi tehtävän.
 * Kutsutaan joka koostossa samassa kohdassa (ei ehdollisesti), jotta siirtymä huomataan.
 */
@Composable
internal fun SoonVoiceEffect(soon: Boolean, key: String, nextName: String?, nextVoicePath: String = "") {
    val context = LocalContext.current
    val speech = remember { mutableStateOf<Speech?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            speech.value?.close()
            speech.value = null
        }
    }
    LaunchedEffect(soon, key) {
        if (!soon || SoonVoiceRuntime.lastPlayedKey == key) return@LaunchedEffect
        SoonVoiceRuntime.lastPlayedKey = key
        val plan = SoonVoice.plan(
            mode = SoonVoice.mode(context),
            clientRecording = SoonVoice.clientRecording(context),
            generalRecording = SoonVoice.generalRecording(context),
            nextName = nextName,
            nextVoicePath = nextVoicePath,
            muted = SoonVoiceRuntime.muted(context),
            soonText = context.getString(R.string.transition_soon_title),
            nextText = { context.getString(R.string.run_next_line, it) }
        )
        when (plan) {
            is SoonVoicePlan.Speak -> {
                val s = speech.value ?: Speech(context.applicationContext).also { speech.value = it }
                s.speak(plan.text)
            }
            is SoonVoicePlan.Play -> VoicePlayer.playSequence(context, plan.paths)
            SoonVoicePlan.None -> Unit
        }
    }
}
