package fi.aikapolku.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun P011EditableCard(
    label: String,
    name: String,
    description: String,
    imagePath: String,
    modifier: Modifier,
    /** false = ohjaajan lukitus päällä: "Muokkaa" piilotetaan. */
    editable: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                color = P011Colors.teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            P011Photo(
                name = name,
                imagePath = imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Text(
                name,
                color = P011Colors.ink,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (description.isNotBlank()) {
                Text(
                    description,
                    color = P011Colors.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            if (editable) {
                Text(
                    "Muokkaa ↗",
                    color = P011Colors.teal,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun P011HeroCard(
    label: String,
    name: String,
    imagePath: String,
    large: Boolean
) {
    val shortScreen =
        LocalConfiguration.current.screenHeightDp < 740

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = P011Colors.soft,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = P011Colors.teal
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        if (large && !shortScreen) 275.dp
                        else if (large) 218.dp
                        else 162.dp
                    )
                    .clip(RoundedCornerShape(16.dp))
            ) {
                P011Photo(name, imagePath, Modifier.fillMaxSize())
            }

            Text(
                name,
                modifier = Modifier.padding(
                    start = 8.dp,
                    bottom = 5.dp
                ),
                fontSize = if (large) 26.sp else 23.sp,
                fontWeight = FontWeight.Bold,
                color = P011Colors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun P011CompletedCard(
    first: String,
    next: String,
    nextPhoto: String
) {
    Text(
        "✓ $first valmis",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF166534)
    )

    if (next != "Valitse toiminto") {
        P011HeroCard(
            label = "SEURAAVAKSI",
            name = next,
            imagePath = nextPhoto,
            large = false
        )
    } else {
        Text(
            "Kaikki tehty!",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = P011Colors.ink
        )
    }
}