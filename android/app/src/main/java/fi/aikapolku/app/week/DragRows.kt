package fi.aikapolku.app.week

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import fi.aikapolku.app.data.Runko
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.LocalPageScroll

/**
 * Rivien raahaus pitkällä painalluksella (Tänään-lista ja viikkomuokkain). Raahattu rivi
 * seuraa sormea, sivu vierii reunojen lähellä ja pudotuskohta lasketaan muiden rivien
 * keskikohdista. Rivien paikat ovat ikkunan koordinaateissa.
 */
internal class RowDrag {
    var dragId by mutableStateOf<String?>(null)
    var pointerY by mutableFloatStateOf(0f)
    var grab by mutableFloatStateOf(0f)
    /** Tunnus → (yläreuna, korkeus) ikkunassa. */
    val bounds = mutableStateMapOf<String, Pair<Float, Float>>()

    /** Monenneksi raahattu rivi tulisi muiden joukossa ([others] ylhäältä alas). */
    fun target(others: List<String>): Int =
        Runko.dropIndex(others.map { id -> bounds[id]?.let { (top, h) -> top + h / 2 } ?: 0f }, pointerY)
}

@Composable
internal fun rememberRowDrag(): RowDrag = remember { RowDrag() }

/** Sivu vierii, kun raahattu rivi on lähellä näkyvän alueen ylä- tai alareunaa. */
@Composable
internal fun AutoScrollWhileDragging(drag: RowDrag) {
    val pageScroll = LocalPageScroll.current
    val density = LocalDensity.current
    val dragging = drag.dragId
    LaunchedEffect(dragging) {
        val scroll = pageScroll ?: return@LaunchedEffect
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { 88.dp.toPx() }
        val maxSpeed = with(density) { 22.dp.toPx() }
        while (drag.dragId != null) {
            val y = drag.pointerY
            val speed = when {
                y < scroll.viewportTop + edge -> -maxSpeed * ((scroll.viewportTop + edge - y) / edge).coerceIn(0f, 1f)
                y > scroll.viewportBottom - edge -> maxSpeed * ((y - (scroll.viewportBottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) scroll.state.scrollBy(speed)
            withFrameNanos { }
        }
    }
}

/**
 * Raahattava rivi. [enabled] = voi raahata; muuten pitkä painallus kutsuu [onBlocked]
 * (esim. kertoo, miksi siirto ei ole mahdollinen). [onDrop] kutsutaan pudotuksessa.
 */
internal fun Modifier.dragRow(
    drag: RowDrag,
    id: String,
    enabled: Boolean,
    haptic: HapticFeedback,
    onDrop: () -> Unit,
    onBlocked: (() -> Unit)? = null
): Modifier = this
    .onGloballyPositioned { c -> drag.bounds[id] = c.positionInWindow().y to c.size.height.toFloat() }
    .then(
        when {
            enabled -> Modifier.pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        drag.dragId = id
                        drag.grab = offset.y
                        drag.pointerY = (drag.bounds[id]?.first ?: 0f) + offset.y
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        drag.pointerY = (drag.bounds[id]?.first ?: 0f) + change.position.y
                    },
                    onDragEnd = { if (drag.dragId == id) onDrop() },
                    onDragCancel = { if (drag.dragId == id) drag.dragId = null }
                )
            }
            onBlocked != null -> Modifier.pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (awaitLongPressOrCancellation(down.id) != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBlocked()
                    }
                }
            }
            else -> Modifier
        }
    )
    .then(
        if (drag.dragId == id) Modifier.zIndex(2f).graphicsLayer {
            translationY = drag.pointerY - (drag.bounds[id]?.first ?: 0f) - drag.grab
            scaleX = 1.03f
            scaleY = 1.03f
            shadowElevation = 24f
            shape = RoundedCornerShape(16.dp)
            clip = true
        } else Modifier
    )

/** Pudotuskohdan viiva ja aika. Piirretään rivin päälle, joten se ei siirrä muita rivejä. */
@Composable
internal fun DropLine(time: String, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Teal))
        Text(
            time.replace(':', '.'),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Teal).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
