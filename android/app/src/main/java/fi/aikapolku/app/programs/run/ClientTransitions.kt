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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.delay

/*
 * Siirtymät asiakkaan näkymissä (tutkielma A1/A2):
 *  - "Tarvitsen tauon" on painettavissa kaikissa toimintonäkymissä ilman PIN-koodia
 *    (päiväohjelmassa omana RunSupportButtonina Tarvitsen apua -painikkeen vieressä).
 *  - Ennakkovaroitus kertoo rauhallisesti, mitä seuraavaksi tulee, ennen kuin aika loppuu.
 *  - "Tarvitsen vielä hetken" (A2) antaa lisäaikaa ohjaajan rajoissa (data.MoreTime); kun lisäaikaa
 *    ei voi antaa, se näyttää viestin aikuiselle. Aika ei pysähdy.
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
    val height = pageHeight()
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

/**
 * Tarvitsen vielä hetken -painike. [stacked] = kuvake tekstin yläpuolella (Tarvitsen tauon
 * -painikkeen vieressä, teksti mahtuu kahdelle riville); muuten leveä painike.
 */
@Composable
internal fun MoreTimeButton(onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), stacked: Boolean = false) {
    if (stacked) {
        RunSupportButton(
            label = stringResource(R.string.support_more_time),
            icon = { Icon(AppIcons.MoreTime, contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(24.dp)) },
            onClick = onClick,
            modifier = modifier
        )
    } else {
        SecondaryButton(
            text = stringResource(R.string.support_more_time),
            icon = AppIcons.MoreTime,
            onClick = onClick,
            modifier = modifier
        )
    }
}

/**
 * Lisäaika annettu: rauhallinen vahvistus ("3 min lisää aikaa"). Ei kehuja eikä laskuria.
 * Ruudunlukija lukee sen, kun se ilmestyy.
 */
@Composable
internal fun MoreTimeGrantedCard(minutes: Int, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.more_time_granted, minutes)
    Surface(
        color = AppColors.TealSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, AppColors.Teal),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(AppIcons.MoreTime, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(28.dp))
            Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Lisäajan varattu paikka (56 dp): tyhjä, painike tai vahvistus. Paikka ei muutu kokoaan,
 * joten muu näkymä ei hypi painikkeen ilmestyessä tai vaihtuessa vahvistukseksi.
 */
@Composable
internal fun MoreTimeSlot(visible: Boolean, granted: Int?, onMoreTime: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = MORE_TIME_SLOT_DP.dp), contentAlignment = Alignment.Center) {
        when {
            granted != null -> MoreTimeGrantedCard(granted)
            visible -> MoreTimeButton(onMoreTime, Modifier.fillMaxWidth().heightIn(min = MORE_TIME_SLOT_DP.dp))
        }
    }
}

internal const val MORE_TIME_SLOT_DP = 56

/** Vahvistus näkyy hetken ja häviää itsestään ([state] = annetut minuutit tai null). */
@Composable
internal fun AutoHideGranted(state: MutableState<Int?>) {
    val value = state.value
    LaunchedEffect(value) {
        if (value != null) {
            delay(MORE_TIME_CONFIRM_MS)
            state.value = null
        }
    }
}

internal const val MORE_TIME_CONFIRM_MS = 5_000L

/**
 * Tarvitsen vielä hetken -viesti aikuiselle vierivän sivun sisällä (kuten [BreakMessage]).
 * Aika jatkuu taustalla; Jatka ja takaisin-ele sulkevat viestin.
 */
@Composable
internal fun MoreTimeMessage(onClose: () -> Unit) {
    BackOverride(onClose)
    val height = pageHeight()
    Box(Modifier.fillMaxWidth().height(height)) {
        SupportMessageScreen(
            kind = SupportKind.MORE_TIME,
            timerPaused = false,
            onContinue = onClose,
            onClose = onClose
        )
    }
}

/**
 * Vierivän sivun sisällä koko näytön viestin korkeus: näytön korkeus miinus tilarivi, yläpalkki,
 * roolipalkki, järjestelmän alapalkki ja sivun reunat. Jatka-painike näkyy aina ilman vieritystä
 * (laatuportti R11).
 */
@Composable
internal fun pageHeight(): androidx.compose.ui.unit.Dp =
    (LocalConfiguration.current.screenHeightDp - PAGE_CHROME_DP).coerceAtLeast(360).dp

internal const val PAGE_CHROME_DP = 180
