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
internal fun FlowModePicker(
    timerSelected: Boolean,
    onTimer: () -> Unit,
    onNoLimit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        FlowModeChoice(
            symbol = "⏱",
            title = stringResource(R.string.ajastimella),
            description = stringResource(R.string.valitse_aika_pyorittamalla_kellotaulua),
            selected = timerSelected,
            onClick = onTimer
        )

        FlowModeChoice(
            symbol = "✋",
            title = stringResource(R.string.ilman_aikarajaa),
            description = stringResource(R.string.siirry_eteenpain_kun_olet_valmis),
            selected = !timerSelected,
            onClick = onNoLimit
        )
    }
}

@Composable
private fun FlowModeChoice(
    symbol: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) AppColors.Teal
            else Color(0xFFD9E2E3)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                Color(0xFFE7F5F1)
            else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(symbol, fontSize = 25.sp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.Ink
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = FlowColors.muted
                )
            }

            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}

@Composable
internal fun ManualFlowScreen(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String,
    onNext: () -> Unit
) {
    Text(
        stringResource(R.string.ilman_aikarajaa),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )

    Text(
        stringResource(R.string.nyt),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Teal
    )

    HeroCard(
        label = stringResource(R.string.nyt),
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
