package fi.aikapolku.app.programs.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RunSupportButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    // Kuvake tekstin yläpuolella: teksti mahtuu kahdelle riville myös suurella fontilla.
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, RunColors.border),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = RunColors.card,
            contentColor = RunColors.ink
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun RunHelpIcon(iconSize: Int = 22, color: Color = RunColors.ink) {
    Box(
        modifier = Modifier.size(iconSize.dp).border((iconSize / 11f).coerceAtLeast(2f).dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("?", fontSize = (iconSize * 0.62f).sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
internal fun RunPauseIcon(iconSize: Int = 22, color: Color) {
    Canvas(modifier = Modifier.size(iconSize.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(color, size.minDimension / 2f - stroke / 2f, center, style = Stroke(stroke))
        val h = size.height * 0.28f
        val dx = size.width * 0.11f
        drawLine(color, Offset(center.x - dx, center.y - h), Offset(center.x - dx, center.y + h), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x + dx, center.y - h), Offset(center.x + dx, center.y + h), stroke, StrokeCap.Round)
    }
}
