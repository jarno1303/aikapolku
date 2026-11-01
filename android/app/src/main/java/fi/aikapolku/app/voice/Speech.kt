package fi.aikapolku.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

/** P0-21d: Only exposes voices actually returned by the Android TTS engine.
 * Android Voice has no reliable gender field. Users choose by listening.
 */
internal object VoicePrefs {
    private const val NAME = "aikapolku_p021c_preferences"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun voiceId(ctx: Context) = prefs(ctx).getString("tts_voice_name", "") ?: ""
    fun setVoiceId(ctx: Context, value: String) = prefs(ctx).edit().putString("tts_voice_name", value).apply()
    fun speed(ctx: Context): Float = prefs(ctx).getFloat("tts_rate", 1.0f).coerceIn(0.8f, 1.2f)
    fun setSpeed(ctx: Context, value: Float) = prefs(ctx).edit().putFloat("tts_rate", value.coerceIn(0.8f, 1.2f)).apply()
}

/** TTS init is async: a message requested before init is queued instead of lost. */
internal class Speech(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var closed = false
    private var pending: Pair<String, String>? = null
    var ready by mutableStateOf(false)
        private set
    var status by mutableStateOf("Ladataan laitteen puheääniä…")
        private set
    var finnishVoices by mutableStateOf<List<Voice>>(emptyList())
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            mainHandler.post {
                if (!closed) {
                    val speaker = engine
                    if (result != TextToSpeech.SUCCESS || speaker == null) {
                        status = "Puheääniä ei voitu avata. Tarkista Androidin puheasetukset."
                    } else {
                        runCatching { speaker.language = Locale.forLanguageTag("fi-FI") }
                        finnishVoices = runCatching {
                            speaker.voices.orEmpty()
                                .filter { it.locale.language.equals("fi", ignoreCase = true) }
                                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
                        }.getOrDefault(emptyList())
                        ready = true
                        status = if (finnishVoices.isEmpty())
                            "Erillisiä suomenkielisiä ääniä ei löytynyt. Kokeile laitteen oletusääntä."
                        else "Valitse ja kuuntele haluamasi suomenkielinen ääni."
                        val request = pending
                        pending = null
                        if (request != null) speak(request.first, request.second)
                    }
                }
            }
        }
    }

    fun speak(text: String, selectedId: String = VoicePrefs.voiceId(context)) {
        if (closed) return
        if (!ready) {
            pending = text to selectedId
            return
        }
        val speaker = engine ?: return
        val voice = finnishVoices.firstOrNull { it.name == selectedId }
        val configured = runCatching {
            if (voice != null) speaker.voice = voice
            else speaker.language = Locale.forLanguageTag("fi-FI")
            speaker.setSpeechRate(VoicePrefs.speed(context))
            speaker.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aikapolku_support")
        }.getOrNull()
        if (configured != TextToSpeech.SUCCESS) {
            status = "Puhetta ei voitu toistaa. Kokeile toista ääntä tai laitteen oletusääntä."
        }
    }

    fun close() {
        closed = true
        pending = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
