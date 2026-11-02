package fi.aikapolku.app.programs.run

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import fi.aikapolku.app.ui.AppIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.SubstepArt
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.voice.Speech
import fi.aikapolku.app.voice.VoicePlayer

/**
 * Tarvitsen apua / Tarvitsen tauon: koko näytön viesti, jonka voi näyttää toiselle.
 * Ajastin on pysäytetty pyynnön yhteydessä. Jatka palauttaa tilanteen ilman PIN-koodia.
 */
@Composable
internal fun SupportMessageScreen(
    kind: SupportKind,
    timerPaused: Boolean,
    onContinue: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Teksti tulee käännettävistä resursseista.
    val message = stringResource(if (kind == SupportKind.BREAK) R.string.support_break else R.string.support_help)
    val voiceMode = remember(kind) { RunPreferences.voiceMode(context, kind.key) }
    val recording = remember(kind) { RunPreferences.recording(context, kind.key) }
    val speech = remember(context) { Speech(context.applicationContext) }
    DisposableEffect(speech) {
        onDispose {
            speech.close()
            VoicePlayer.stop()
        }
    }
    fun playConfigured() {
        when (voiceMode) {
            RunPreferences.AUDIO_TTS -> speech.speak(message)
            RunPreferences.AUDIO_RECORDED ->
                if (recording.isNotBlank()) VoicePlayer.play(context, recording)
            else -> Unit
        }
    }
    // Käyttäjä on valinnut äänen asetuksista: yksi painallus riittää viestimään.
    LaunchedEffect(message, voiceMode, recording) { playConfigured() }
    // Takaisin-ele sulkee viestin eikä poistu päiväohjelmasta.
    BackHandler(onBack = onClose)

    val image = SubstepArt.messageImageRes(kind)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RunColors.background)) {
        val compact = maxWidth > maxHeight || maxHeight < 440.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.heightIn(min = 52.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    if (timerPaused) stringResource(R.string.ajastin_on_pysaytetty) else stringResource(R.string.nayta_viesti_tarvittaessa_toiselle),
                    fontSize = 15.sp,
                    color = RunColors.secondary
                )
            }

            Card(
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(3.dp, RunColors.tealSoft),
                colors = CardDefaults.cardColors(containerColor = RunColors.card)
            ) {
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Image(
                        painter = painterResource(image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = (if (compact) Modifier.size(160.dp) else Modifier
                            .weight(1f, fill = false)
                            .sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                            .aspectRatio(1f))
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Text(
                        message,
                        modifier = Modifier.semantics { heading() },
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunColors.ink,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (voiceMode != RunPreferences.AUDIO_SILENT) {
                OutlinedButton(
                    onClick = { playConfigured() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, RunColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = RunColors.card,
                        contentColor = RunColors.ink
                    )
                ) {
                    Icon(AppIcons.Speaker, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.toista_viesti), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunColors.teal,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.support_continue), fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
