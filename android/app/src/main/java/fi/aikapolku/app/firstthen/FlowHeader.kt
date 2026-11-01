package fi.aikapolku.app.firstthen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.FlowColors

@Composable
internal fun FlowHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowStage(stringResource(R.string.flow_stage_first), true, compact)
        Text("→", fontSize = 18.sp, color = FlowColors.muted)
        FlowStage(stringResource(R.string.flow_stage_then), false, compact)
    }
}

@Composable
private fun FlowStage(
    label: String,
    selected: Boolean,
    compact: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (selected) FlowColors.teal else FlowColors.soft
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = 9.dp
            ),
            color = if (selected) Color.White else FlowColors.teal,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
