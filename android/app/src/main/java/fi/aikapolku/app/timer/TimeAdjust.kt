package fi.aikapolku.app.timer

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Ajan asetus painikkeilla (puhelintesti 15.11.2026: pieneltä taululta hankala, käsi edessä). */
internal object TimeAdjust {
    const val MIN = 1
    const val MAX = 60
    val QUICK = listOf(5, 10, 15, 30)
    const val MINUTES_TAG = "timer_adjust_minutes"

    /** Napautus: minuutti kerrallaan. */
    fun step(minutes: Int, up: Boolean): Int = (minutes + if (up) 1 else -1).coerceIn(MIN, MAX)

    /** Pitkä painallus: seuraavaan viidellä jaolliseen (15 → 20 → 25, 15 → 10 → 5). */
    fun jump(minutes: Int, up: Boolean): Int =
        (if (up) (minutes / 5 + 1) * 5 else ((minutes - 1) / 5) * 5).coerceIn(MIN, MAX)
}

/**
 * − 15 min + ja pikavalinnat. Vain ohjaajalle ennen käynnistystä (kutsuja päättää); aika näkyy
 * isona taulun ulkopuolella, joten käsi ei peitä sitä.
 */
@Composable
internal fun TimeAdjustControls(minutes: Int, onSet: (Int) -> Unit) {
    val current by rememberUpdatedState(minutes)
    val set by rememberUpdatedState(onSet)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StepButton(up = false, enabled = minutes > TimeAdjust.MIN, minutes = { current }, onSet = { set(it) })
            Text(
                stringResource(R.string.timer_minutes_big, minutes),
                modifier = Modifier.widthIn(min = 110.dp).testTag(TimeAdjust.MINUTES_TAG),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            StepButton(up = true, enabled = minutes < TimeAdjust.MAX, minutes = { current }, onSet = { set(it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeAdjust.QUICK.forEach { m ->
                FilterChip(
                    selected = m == minutes,
                    onClick = { set(m) },
                    label = { Text(stringResource(R.string.timer_minutes_big, m), fontSize = 15.sp) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun StepButton(up: Boolean, enabled: Boolean, minutes: () -> Int, onSet: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val description = stringResource(if (up) R.string.timer_plus else R.string.timer_minus)
    val color = if (enabled) AppColors.Teal else AppColors.Muted.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(2.dp, color, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (enabled) onSet(TimeAdjust.step(minutes(), up))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    onSet(TimeAdjust.step(minutes(), up))
                    // Pidettäessä 5 minuutin askelin, kunnes sormi nousee.
                    val repeat = scope.launch {
                        delay(500)
                        while (isActive) {
                            onSet(TimeAdjust.jump(minutes(), up))
                            delay(400)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(if (up) Icons.Rounded.Add else AppIcons.Minus, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
    }
}
