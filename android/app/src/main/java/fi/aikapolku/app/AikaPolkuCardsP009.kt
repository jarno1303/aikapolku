package fi.aikapolku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val P09Teal = Color(0xFF0F766E)
private val P09Ink = Color(0xFF1F2937)
private val P09Muted = Color(0xFF475569)
private val P09Soft = Color(0xFFE6F4F1)

private fun p09Emoji(name: String): String = when (name) {
    "Askartelu" -> "🎨"
    "Ruokailu" -> "🍽️"
    "Ulkoilu" -> "🚶"
    "Pelaaminen" -> "🎮"
    "Lukeminen" -> "📖"
    "Musiikki" -> "🎵"
    "Tehtävät" -> "🧩"
    "Tauko" -> "☕"
    "Lepo" -> "🛏️"
    "Hampaiden pesu" -> "🪥"
    "WC" -> "🚽"
    "Pukeutuminen" -> "🧥"
    else -> "🖼️"
}

private fun p09LoadBitmap(path: String): Bitmap? {
    if (path.isBlank()) return null

    return runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@runCatching null
        }

        var sample = 1
        while (
            bounds.outWidth / sample > 900 ||
            bounds.outHeight / sample > 900
        ) {
            sample *= 2
        }

        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
            }
        )
    }.getOrNull()
}

@Composable
private fun P09Photo(
    name: String,
    imagePath: String,
    modifier: Modifier = Modifier
) {
    P011Photo(name = name, imagePath = imagePath, modifier = modifier)
}

@Composable
private fun P09StageLabel(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = if (active) P09Teal
                else Color.White.copy(alpha = 0.95f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = 6.dp
            ),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = if (active) Color.White else P09Teal
        )
    }
}

@Composable
private fun P09CardBody(
    label: String,
    name: String,
    imagePath: String,
    description: String = ""
) {
    val narrow =
        LocalConfiguration.current.screenWidthDp < 370

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        P09StageLabel(
            text = label,
            active = label == "ENSIN"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (narrow) 1.5f else 1.4f)
                .clip(RoundedCornerShape(15.dp))
        ) {
            P09Photo(
                name = name,
                imagePath = imagePath,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = P09Ink,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (description.isNotBlank()) {
            Text(
                description,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = P09Muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PolishedEditableCard(
    label: String,
    name: String,
    description: String,
    imagePath: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 160.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.padding(7.dp)) {
            P09CardBody(
                label = label,
                name = name,
                imagePath = imagePath,
                description = description
            )
        }
    }
}

@Composable
private fun P09ReadOnlyCard(
    label: String,
    name: String,
    imagePath: String,
    modifier: Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 155.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.padding(7.dp)) {
            P09CardBody(
                label = label,
                name = name,
                imagePath = imagePath
            )
        }
    }
}

@Composable
internal fun PolishedActiveCards(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        P09ReadOnlyCard(
            label = "ENSIN",
            name = first,
            imagePath = firstPhoto,
            modifier = Modifier.weight(1f)
        )
        P09ReadOnlyCard(
            label = "SITTEN",
            name = next,
            imagePath = nextPhoto,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun P09HeroPhoto(
    name: String,
    imagePath: String,
    label: String,
    height: androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            P09StageLabel(text = label, active = true)
            P09Photo(
                name = name,
                imagePath = imagePath,
                modifier = Modifier.fillMaxWidth()
                    .height(height - 95.dp)
                    .clip(RoundedCornerShape(15.dp))
            )
            Text(
                name,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                color = P09Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PolishedCompletedCards(
    first: String,
    next: String,
    nextPhoto: String
) {
    val compact =
        LocalConfiguration.current.screenHeightDp < 740

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "✓ $first valmis",
            color = Color(0xFF166534),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (next == "Valitse toiminto") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = P09Soft
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Kaikki tehty!",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = P09Ink
                )
            }
        } else {
            P09HeroPhoto(
                name = next,
                imagePath = nextPhoto,
                label = "SEURAAVAKSI",
                height = if (compact) 180.dp else 220.dp
            )
        }
    }
}

@Composable
internal fun PolishedLastActivityScreen(
    name: String,
    imagePath: String,
    onFinish: () -> Unit,
    onAddNext: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 740
    val narrow = configuration.screenWidthDp < 370

    P09HeroPhoto(
        name = name,
        imagePath = imagePath,
        label = "VIIMEINEN TOIMINTO · NYT",
        height = if (compact) 245.dp else 330.dp
    )

    if (narrow) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = P09Teal
                )
            ) {
                Text("✓ Valmis", fontSize = 17.sp)
            }

            OutlinedButton(
                onClick = onAddNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
            ) {
                Text("+ Lisää vaihe", fontSize = 16.sp)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = P09Teal
                )
            ) {
                Text("✓ Valmis", fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = onAddNext,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
            ) {
                Text("+ Lisää vaihe", fontSize = 15.sp)
            }
        }
    }
}