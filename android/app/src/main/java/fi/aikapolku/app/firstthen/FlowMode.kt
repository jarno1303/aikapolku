package fi.aikapolku.app.firstthen

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
    Text(
        stringResource(R.string.omaan_tahtiin),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )

    Text(
        stringResource(R.string.now_label),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Teal
    )

    HeroCard(
        label = stringResource(R.string.now_label),
        name = first,
        imagePath = firstPhoto,
        large = false
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardPhoto(
                name = next,
                imagePath = nextPhoto,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.seuraavaksi),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Teal
                )
                Text(
                    next,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Ink
                )
            }
        }
    }

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
        Text(
            stringResource(R.string.valmis_seuraavaan),
            fontSize = 18.sp
        )
    }
}
