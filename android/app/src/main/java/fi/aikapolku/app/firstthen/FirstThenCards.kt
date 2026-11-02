package fi.aikapolku.app.firstthen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Ensin–Sitten-pari: kaksi korttia vierekkäin ja iso nuoli välissä. ENSIN-kortissa tumma
 * yläpalkki ja korostettu reunus (tehdään nyt), SITTEN-kortissa vaalea palkki. Sama ulkoasu
 * valintanäkymässä ja ajastimen yläosassa ([compact]).
 */
@Composable
internal fun FirstThenPair(
    first: String,
    firstPhoto: String,
    firstDescription: String,
    next: String,
    nextPhoto: String,
    nextDescription: String,
    editable: Boolean,
    compact: Boolean = false,
    onFirstClick: () -> Unit = {},
    onNextClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PairCard(
            label = stringResource(R.string.first_label),
            primary = true,
            name = first,
            description = firstDescription,
            imagePath = firstPhoto,
            editable = editable,
            compact = compact,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onFirstClick
        )
        Box(
            modifier = Modifier.padding(horizontal = 4.dp).size(if (compact) 30.dp else 36.dp).clip(CircleShape).background(FlowColors.teal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
        }
        PairCard(
            label = stringResource(R.string.then_label),
            primary = false,
            name = next,
            description = nextDescription,
            imagePath = nextPhoto,
            editable = editable,
            compact = compact,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onNextClick
        )
    }
}

@Composable
private fun PairCard(
    label: String,
    primary: Boolean,
    name: String,
    description: String,
    imagePath: String,
    editable: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val cardModifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $name" }
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    val border = BorderStroke(if (primary) 3.dp else 1.dp, if (primary) FlowColors.teal else Color(0xFFD9E2E3))
    val elevation = CardDefaults.cardElevation(if (primary) 3.dp else 1.dp)
    // Lukittuna ja ajastimessa kortti ei ole painike (ei "ei käytössä" -ilmoitusta ruudunlukijalle).
    if (editable) {
        Card(onClick = onClick, modifier = cardModifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            PairCardContent(label, primary, name, description, imagePath, editable, compact)
        }
    } else {
        Card(modifier = cardModifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            PairCardContent(label, primary, name, description, imagePath, editable, compact)
        }
    }
}

@Composable
private fun PairCardContent(
    label: String,
    primary: Boolean,
    name: String,
    description: String,
    imagePath: String,
    editable: Boolean,
    compact: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Värillinen yläpalkki: ENSIN tumma, SITTEN vaalea.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (primary) FlowColors.teal else FlowColors.soft)
                .padding(vertical = if (compact) 5.dp else 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (primary) Color.White else FlowColors.teal,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CardPhoto(
                name = name,
                imagePath = imagePath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 88.dp else 120.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Text(
                name,
                color = FlowColors.ink,
                fontSize = if (compact) 16.sp else 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact && description.isNotBlank()) {
                Text(
                    description,
                    color = FlowColors.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (editable && !compact) {
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
