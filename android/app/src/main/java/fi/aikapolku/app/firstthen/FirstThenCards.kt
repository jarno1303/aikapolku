package fi.aikapolku.app.firstthen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.ui.FlowColors

@Composable
internal fun EditableCard(
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
                color = FlowColors.teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            CardPhoto(
                name = name,
                imagePath = imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Text(
                name,
                color = FlowColors.ink,
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
                    color = FlowColors.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            if (editable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = FlowColors.teal, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.card_edit_link),
                        color = FlowColors.teal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeroCard(
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
                color = FlowColors.soft,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = FlowColors.teal
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
                CardPhoto(name, imagePath, Modifier.fillMaxSize())
            }

            Text(
                name,
                modifier = Modifier.padding(
                    start = 8.dp,
                    bottom = 5.dp
                ),
                fontSize = if (large) 26.sp else 23.sp,
                fontWeight = FontWeight.Bold,
                color = FlowColors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CompletedCard(
    first: String,
    next: String,
    nextPhoto: String
) {
    Text(
        stringResource(R.string.first_then_done, first),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF166534)
    )

    if (next != stringResource(R.string.choose_activity)) {
        HeroCard(
            label = stringResource(R.string.seuraavaksi),
            name = next,
            imagePath = nextPhoto,
            large = false
        )
    } else {
        Text(
            stringResource(R.string.kaikki_tehty),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = FlowColors.ink
        )
    }
}
