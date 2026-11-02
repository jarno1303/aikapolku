package fi.aikapolku.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Teksti, joka pienenee, kunnes yksikään sana ei katkea kesken ("Pukeutumine / n") eikä
 * rivejä ole yli [maxLines]. Kokeiltu koko piirretään vasta sopivana, joten välkettä ei näy.
 */
@Composable
internal fun FitText(
    text: String,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 11.sp,
    maxLines: Int = 2,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) {
    var size by remember(text, maxFontSize) { mutableFloatStateOf(maxFontSize.value) }
    var ready by remember(text, maxFontSize) { mutableStateOf(false) }
    androidx.compose.material3.Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        onTextLayout = { layout ->
            if (!fits(layout) && size > minFontSize.value) {
                size = (size - 1f).coerceAtLeast(minFontSize.value)
            } else {
                ready = true
            }
        }
    )
}

private fun fits(layout: TextLayoutResult): Boolean {
    if (layout.hasVisualOverflow) return false
    val text = layout.layoutInput.text.text
    val lineEnds = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
    return !TextFit.breaksWord(text, lineEnds)
}

internal object TextFit {
    /** Katkeaako jokin sana rivinvaihdon kohdalla? [lineEnds] = rivien loppukohdat (ei viimeistä). */
    fun breaksWord(text: String, lineEnds: List<Int>): Boolean = lineEnds.any { end ->
        end in 1 until text.length && !text[end - 1].isWhitespace() && text[end - 1] != '-' && !text[end].isWhitespace()
    }
}
