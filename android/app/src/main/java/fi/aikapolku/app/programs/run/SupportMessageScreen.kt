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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
 * Tarvitsen apua / Tarvitsen tauon / Tarvitsen vielä hetken: koko näytön viesti, jonka voi näyttää toiselle.
 * Apu ja tauko pysäyttävät ajastimen pyynnön yhteydessä; lisäaikapyyntö ei. Jatka palauttaa tilanteen ilman PIN-koodia.
 */
@Composable
internal fun SupportMessageScreen(
    kind: SupportKind,
    timerPaused: Boolean,
    onContinue: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Oma tila tauon aikana (asiakkaan oma tieto, ks. OwnStateFlow).
    var ownStateOpen by rememberSaveable { mutableStateOf(false) }
    if (ownStateOpen) {
        OwnStateFlow(onClose = { ownStateOpen = false })
        return
    }
    val canTell = remember { fi.aikapolku.app.link.ClientMessages.canSend(context) }
    val showOwnState = kind == SupportKind.BREAK && remember { fi.aikapolku.app.data.OwnState.enabled(context) }
    var sent by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Teksti tulee käännettävistä resursseista.
    val message = stringResource(
        when (kind) {
            SupportKind.BREAK -> R.string.support_break
            SupportKind.HELP -> R.string.support_help
            SupportKind.MORE_TIME -> R.string.support_more_time
            SupportKind.CHOICE_NONE -> R.string.support_choice_none
        }
    )
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
                    when {
                        timerPaused -> stringResource(R.string.ajastin_on_pysaytetty)
                        // Lisäaikapyyntö ei pysäytä aikaa: kerrotaan se, ettei asiakas jää odottamaan.
                        kind == SupportKind.MORE_TIME -> stringResource(R.string.more_time_show_adult)
                        else -> stringResource(R.string.nayta_viesti_tarvittaessa_toiselle)
                    },
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

            // Kerro ohjaajalle (asiakas päättää joka kerta) ja oma tila tauolla.
            if (canTell || showOwnState) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canTell) {
                        OutlinedButton(
                            onClick = {
                                if (!sending) {
                                    sending = true
                                    scope.launch {
                                        sent = fi.aikapolku.app.link.ClientMessages.send(context, kind.key, message)
                                        sending = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, RunColors.teal),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = RunColors.card, contentColor = RunColors.teal)
                        ) {
                            Text(
                                when {
                                    sending -> stringResource(R.string.link_sending)
                                    sent == true -> stringResource(R.string.link_sent_ok)
                                    else -> stringResource(R.string.link_tell_caregiver)
                                },
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (showOwnState) {
                        OutlinedButton(
                            onClick = { ownStateOpen = true },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, RunColors.teal),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = RunColors.card, contentColor = RunColors.teal)
                        ) { Text(stringResource(R.string.own_state_open), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                if (sent == false) {
                    Text(
                        stringResource(R.string.link_sent_failed),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 15.sp, color = fi.aikapolku.app.ui.AppColors.Coral, textAlign = TextAlign.Center
                    )
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
