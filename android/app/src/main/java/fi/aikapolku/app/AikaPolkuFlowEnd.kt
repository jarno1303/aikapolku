package fi.aikapolku.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
internal fun LastActivityScreen(
    name: String,
    imagePath: String,
    onFinish: () -> Unit,
    onAddNext: () -> Unit
) {
    val bitmap = remember(imagePath) {
        if (imagePath.isBlank()) null else runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, bounds)

            var sample = 1
            while (
                bounds.outWidth / sample > 600 ||
                bounds.outHeight / sample > 600
            ) {
                sample *= 2
            }

            BitmapFactory.decodeFile(
                imagePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
            )
        }.getOrNull()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "VIIMEINEN TOIMINTO",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF475569)
        )

        Surface(
            color = Color(0xFFE6F4F1),
            shape = RoundedCornerShape(50.dp)
        ) {
            Text(
                "NYT",
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                ),
                color = Color(0xFF0F766E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .background(
                            Color(0xFFE6F4F1),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (name) {
                            "Askartelu" -> "🎨"
                            "Ruokailu" -> "🍽️"
                            "Ulkoilu" -> "🚶"
                            "Pelaaminen" -> "🎮"
                            "Lukeminen" -> "📖"
                            "Musiikki" -> "🎵"
                            "Lepo" -> "🛏️"
                            else -> "✓"
                        },
                        fontSize = 70.sp
                    )
                }
            }

            Text(
                name,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                textAlign = TextAlign.Center
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onFinish,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F766E)
            )
        ) {
            Text("✓ Valmis", fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = onAddNext,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 54.dp)
        ) {
            Text("+ Lisää vaihe", fontSize = 14.sp)
        }
    }
}
@Composable
internal fun FlowDoneScreen(
    onHome: () -> Unit
) {
    Spacer(Modifier.height(34.dp))

    Text(
        "✓",
        fontSize = 72.sp,
        color = Color(0xFF166534),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Text(
        "Kaikki valmista!",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1F2937),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Text(
        "Ohjelma on päättynyt.",
        fontSize = 17.sp,
        color = Color(0xFF475569),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onHome,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0F766E)
        )
    ) {
        Text("Takaisin aloitukseen")
    }
}