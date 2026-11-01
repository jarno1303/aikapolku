package fi.aikapolku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ActiveFirstThenCards(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActiveTaskCard(
            label = "ENSIN",
            name = first,
            photo = firstPhoto,
            modifier = Modifier.weight(1f)
        )
        ActiveTaskCard(
            label = "SITTEN",
            name = next,
            photo = nextPhoto,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActiveTaskCard(
    label: String,
    name: String,
    photo: String,
    modifier: Modifier
) {
    val bitmap = remember(photo) { loadSmallPhoto(photo) }
    val symbol = when (name) {
        "Askartelu" -> "🎨"
        "Ruokailu" -> "🍽️"
        "Ulkoilu" -> "🚶"
        "Pelaaminen" -> "🎮"
        "Lukeminen" -> "📖"
        "Musiikki" -> "🎵"
        "Tauko" -> "☕"
        "Lepo" -> "🛏️"
        "Pukeutuminen" -> "🧥"
        else -> "📷"
    }

    Card(
        modifier = modifier.heightIn(min = 145.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                fontSize = 13.sp,
                color = Color(0xFF0F766E),
                fontWeight = FontWeight.Bold
            )

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                )
            } else {
                Text(symbol, fontSize = 43.sp)
            }

            Text(
                name,
                fontSize = 16.sp,
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

private fun loadSmallPhoto(path: String): Bitmap? {
    if (path.isBlank()) return null

    return runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        while (
            bounds.outWidth / sample > 512 ||
            bounds.outHeight / sample > 512
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
internal fun CompletedFirstThenCards(
    first: String,
    next: String,
    nextPhoto: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE6F4F1)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "✓ $first valmis",
                fontSize = 15.sp,
                color = Color(0xFF166534)
            )

            if (next == "Valitse toiminto") {
                Text(
                    "Kaikki tehty!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    "SEURAAVAKSI",
                    fontSize = 14.sp,
                    color = Color(0xFF0F766E),
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val bitmap = remember(nextPhoto) {
                        loadSmallPhoto(nextPhoto)
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(110.dp)
                        )
                    } else {
                        Text("➡", fontSize = 42.sp)
                    }

                    Text(
                        next,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                }
            }
        }
    }
}