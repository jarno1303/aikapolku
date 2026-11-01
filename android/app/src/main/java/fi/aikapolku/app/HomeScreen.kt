package fi.aikapolku.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver

@Composable
internal fun HomeScreen(open: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ap_home_brand),
            contentDescription = null,
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(17.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("AikaPolku", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Selkeyttä arkeen, askel kerrallaan", fontSize = 14.sp, color = Secondary)
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Mitä haluat tehdä?", fontSize = 22.sp,
        fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(6.dp))

    HomeCard(
        symbol = "timer",
        title = "Ajastin",
        subtitle = "Aseta aika ja keskity",
        onClick = { open("timer") }
    )
    HomeCard(
        symbol = "flow",
        title = "Ensin – Sitten",
        subtitle = "Suunnittele kaksi askelta",
        onClick = { open("firstthen") }
    )
    HomeCard(
        symbol = "programs",
        title = "Päiväohjelmat",
        subtitle = "Näe päivän kulku",
        onClick = { open("programs") }
    )

    val caregiver = LocalCaregiver.current
    TextButton(
        onClick = { caregiver.run(AppAction.SETTINGS) { open("caregiver") } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            if (caregiver.isLocked) "🔒 Ohjaajan asetukset" else "⚙ Ohjaajan asetukset",
            color = Teal,
            fontSize = 15.sp
        )
    }

    Spacer(Modifier.height(14.dp))
    Surface(
        color = Color(0xFFE6F4F1),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(
            "Näe, mitä teet nyt ja mitä seuraavaksi.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp),
            fontSize = 14.sp,
            color = Color(0xFF145B55)
        )
    }
}

@Composable
private fun HomeCard(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val icon = when (symbol) {
        "flow" -> R.drawable.ap_home_flow
        "programs" -> R.drawable.ap_home_programs
        else -> R.drawable.ap_home_timer
    }
    val surfaceColor = when (symbol) {
        "flow" -> Color(0xFFF1ECFB)
        "programs" -> Color(0xFFE8F3FA)
        else -> Color(0xFFE4F4EF)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(subtitle, fontSize = 14.sp, color = Secondary)
            }
            Text("›", fontSize = 28.sp, color = Teal)
        }
    }
}
