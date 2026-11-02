package fi.aikapolku.app.firstthen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.FlowColors

@Composable
internal fun ManualFlowScreen(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String,
    onNext: () -> Unit
) {
    // Sama Ensin–Sitten-pari kuin valinnassa ja ajastimessa; tila kerrotaan pienellä tekstillä.
    Text(
        stringResource(R.string.omaan_tahtiin),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Muted
    )

    FirstThenPair(
        first = first,
        firstPhoto = firstPhoto,
        firstDescription = "",
        next = next,
        nextPhoto = nextPhoto,
        nextDescription = "",
        editable = false
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Teal
        )
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(
            stringResource(R.string.valmis_seuraavaan),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
