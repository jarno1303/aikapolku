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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.cards.CardPhoto

private val ModeTeal = Color(0xFF0F766E)

private val ModeInk = Color(0xFF1F2937)

private val ModeMuted = Color(0xFF526773)

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
            title = "Ajastimella",
            description = "Valitse aika pyörittämällä kellotaulua",
            selected = timerSelected,
            onClick = onTimer
        )

        FlowModeChoice(
            symbol = "✋",
            title = "Ilman aikarajaa",
            description = "Siirry eteenpäin, kun olet valmis",
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
            if (selected) ModeTeal
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
                    color = ModeInk
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = ModeMuted
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
        "Ilman aikarajaa",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = ModeInk
    )

    Text(
        "NYT",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = ModeTeal
    )

    HeroCard(
        label = "NYT",
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
                    "SEURAAVAKSI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ModeTeal
                )
                Text(
                    next,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ModeInk
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
            containerColor = ModeTeal
        )
    ) {
        Text(
            "Valmis – seuraavaan",
            fontSize = 18.sp
        )
    }
}
