package fi.aikapolku.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** P0-21d: Only exposes voices actually returned by the Android TTS engine.
 * Android Voice has no reliable gender field. Users choose by listening.
 */
internal object P021dVoicePrefs {
    private const val NAME = "aikapolku_p021c_preferences"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun voiceId(ctx: Context) = prefs(ctx).getString("tts_voice_name", "") ?: ""
    fun setVoiceId(ctx: Context, value: String) = prefs(ctx).edit().putString("tts_voice_name", value).apply()
    fun speed(ctx: Context): Float = prefs(ctx).getFloat("tts_rate", 1.0f).coerceIn(0.8f, 1.2f)
    fun setSpeed(ctx: Context, value: Float) = prefs(ctx).edit().putFloat("tts_rate", value.coerceIn(0.8f, 1.2f)).apply()
}

/** TTS init is async: a message requested before init is queued instead of lost. */
internal class P021dSpeech(private val context: Context) {
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

    fun speak(text: String, selectedId: String = P021dVoicePrefs.voiceId(context)) {
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
            speaker.setSpeechRate(P021dVoicePrefs.speed(context))
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

@Composable
internal fun P021dVoicePickerDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val speech = remember(context) { P021dSpeech(context.applicationContext) }
    DisposableEffect(speech) { onDispose { speech.close() } }
    var chosenId by remember { mutableStateOf(P021dVoicePrefs.voiceId(context)) }
    var rate by remember { mutableFloatStateOf(P021dVoicePrefs.speed(context)) }
    val available = speech.finnishVoices
    // A missing or uninstalled selected voice is not shown as a working choice.
    LaunchedEffect(available) {
        if (speech.ready && chosenId.isNotEmpty() && available.none { it.name == chosenId }) {
            chosenId = ""
            P021dVoicePrefs.setVoiceId(context, "")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valitse puheääni") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kuuntele eri ääniä. Android ei ilmoita äänen sukupuolta luotettavasti.", fontSize = 13.sp)
                Text(speech.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                P021dVoiceChoice(
                    label = "Laitteen oletusääni",
                    selected = chosenId.isEmpty(),
                    enabled = speech.ready,
                    onSelect = {
                        chosenId = ""
                        P021dVoicePrefs.setVoiceId(context, "")
                    },
                    onPreview = { speech.speak("Tarvitsen apua. Haluan pitää tauon.", "") }
                )
                available.forEachIndexed { index, voice ->
                    P021dVoiceChoice(
                        label = "Ääni ${index + 1} · ${if (voice.isNetworkConnectionRequired) "vaatii verkon" else "offline"}",
                        selected = chosenId == voice.name,
                        enabled = speech.ready,
                        onSelect = {
                            chosenId = voice.name
                            P021dVoicePrefs.setVoiceId(context, voice.name)
                        },
                        onPreview = { speech.speak("Tarvitsen apua. Haluan pitää tauon.", voice.name) }
                    )
                }
                HorizontalDivider()
                Text("Puheen nopeus", fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(Locale.forLanguageTag("fi-FI"), rate)}×", fontSize = 13.sp)
                Slider(
                    value = rate,
                    onValueChange = {
                        rate = it
                        P021dVoicePrefs.setSpeed(context, it)
                    },
                    valueRange = 0.8f..1.2f,
                    steps = 3
                )
                Text("Puheäänet tulevat puhelimen Android-puhemoottorista. Valikoima vaihtelee laitteittain.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Valmis") }
        }
    )
}

@Composable
private fun P021dVoiceChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        TextButton(onClick = onPreview, enabled = enabled) { Text("Kuuntele") }
    }
}
