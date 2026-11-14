package fi.aikapolku.app.programs.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.BackOverride
import fi.aikapolku.app.ui.SecondaryButton

/*
 * Siirtymät asiakkaan näkymissä (tutkielma A1/A2):
 *  - "Tarvitsen tauon" on painettavissa kaikissa toimintonäkymissä ilman PIN-koodia
 *    (päiväohjelmassa omana RunSupportButtonina Tarvitsen apua -painikkeen vieressä).
 *  - Ennakkovaroitus kertoo rauhallisesti, mitä seuraavaksi tulee, ennen kuin aika loppuu.
 */

/** Tarvitsen tauon -painike: sama ulkoasu muutosnäkymässä, ajastimessa ja tapahtumakortissa. */
@Composable
internal fun BreakButton(onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    SecondaryButton(
        text = stringResource(R.string.support_break),
        icon = AppIcons.Pause,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Tauko vierivän sivun sisällä: sama tukiviesti kuin päiväohjelmassa, rajatulla korkeudella.
 * Yläpalkin takaisin-nuoli ja takaisin-ele sulkevat tauon, eivät koko näkymää.
 */
@Composable
internal fun BreakMessage(timerPaused: Boolean, onContinue: () -> Unit, onClose: () -> Unit) {
    BackOverride(onClose)
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Box(Modifier.fillMaxWidth().height(height)) {
        SupportMessageScreen(
            kind = SupportKind.BREAK,
            timerPaused = timerPaused,
            onContinue = onContinue,
            onClose = onClose
        )
    }
}

/**
 * Ennakkovaroitus: "Pian vaihtuu – Seuraavaksi: X". Ei painettava, ei ääntä, ei punaista
 * eikä liikettä. Ruudunlukija lukee sen, kun se ilmestyy.
 * [image] null = tiivis rivi (Sitten-kortti näkyy jo ruudulla, kuvaa ei toisteta).
 * [nextName] null = ohjaaja on piilottanut seuraavan vaiheen: vain "Pian vaihtuu".
 */
@Composable
internal fun SoonChangeCard(nextName: String?, image: (@Composable (Modifier) -> Unit)?, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.transition_soon_title)
    val line = nextName?.let { stringResource(R.string.run_next_line, it) }
    Surface(
        color = AppColors.TealSoft,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, AppColors.Teal),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (line != null) "$title. $line" else title
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (image != null) 10.dp else 6.dp)
                .heightIn(min = if (image != null) 56.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            image?.invoke(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = if (line == null) 18.sp else 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal)
                if (line != null) Text(
                    line,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
