package fi.aikapolku.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R

/**
 * Sovelluksen yläpalkki kaikissa näkymissä aloitusta lukuun ottamatta: takaisin-nuoli,
 * sivun otsikko ja (kun ohjaajan toiminnot ovat lukossa) lukkopainike aina samassa paikassa.
 */
@Composable
internal fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    showLock: Boolean,
    onUnlock: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    val backLabel = stringResource(R.string.back_a11y)
    val lockLabel = stringResource(R.string.ohjaajan_toiminnot_lukittu_avaa_pin)
    Surface(color = AppColors.Background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { contentDescription = backLabel }
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = AppColors.Ink)
            }
            Text(
                title,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            action?.invoke()
            if (showLock) {
                IconButton(
                    onClick = onUnlock,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = lockLabel }
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppColors.Teal)
                }
            }
        }
    }
}

/**
 * Sisäkkäiset näkymät (esim. päiväohjelman muokkain) voivat vaihtaa yläpalkin otsikon:
 * `ScreenTitle("Uusi päiväohjelma")`. Otsikko palautuu, kun näkymä poistuu.
 */
internal val LocalTitleOverride = compositionLocalOf<MutableState<String?>?> { null }

@Composable
internal fun ScreenTitle(title: String) {
    val holder = LocalTitleOverride.current ?: return
    DisposableEffect(title) {
        holder.value = title
        onDispose { if (holder.value == title) holder.value = null }
    }
}

/**
 * Sisäkkäinen näkymä voi ottaa yläpalkin takaisin-nuolen omaan käyttöönsä (esim. muokkain:
 * edelliseen vaiheeseen tai "Hylätäänkö muutokset?"), jottei nuoli ohita sen omaa logiikkaa.
 */
internal val LocalBackOverride = compositionLocalOf<MutableState<(() -> Unit)?>?> { null }

@Composable
internal fun BackOverride(action: () -> Unit) {
    val holder = LocalBackOverride.current ?: return
    val current = androidx.compose.runtime.rememberUpdatedState(action)
    DisposableEffect(Unit) {
        val wrapper: () -> Unit = { current.value() }
        holder.value = wrapper
        onDispose { if (holder.value === wrapper) holder.value = null }
    }
}

/** Näkymäkohtainen toiminto yläpalkin oikeaan reunaan (esim. ohjeen ⓘ). */
internal val LocalTopBarAction = compositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

@Composable
internal fun TopBarAction(content: @Composable () -> Unit) {
    val holder = LocalTopBarAction.current ?: return
    val current = androidx.compose.runtime.rememberUpdatedState(content)
    DisposableEffect(Unit) {
        val wrapper: @Composable () -> Unit = { current.value() }
        holder.value = wrapper
        onDispose { if (holder.value === wrapper) holder.value = null }
    }
}

/**
 * Sivun vieritys sisäkkäisille näkymille: aikajanan raahaus vierittää sivua, kun kohta
 * viedään lähelle näkyvän alueen ylä- tai alareunaa. [viewportTop]/[viewportBottom] =
 * vieritettävän alueen rajat ikkunassa (pikseleinä).
 */
internal class PageScroll(val state: androidx.compose.foundation.ScrollState) {
    var viewportTop by androidx.compose.runtime.mutableFloatStateOf(0f)
    var viewportBottom by androidx.compose.runtime.mutableFloatStateOf(0f)
}

internal val LocalPageScroll = compositionLocalOf<PageScroll?> { null }
