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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object P011Colors {
    val teal = Color(0xFF0F766E)
    val aqua = Color(0xFF28B6A5)
    val soft = Color(0xFFE7F5F1)
    val ink = Color(0xFF192D38)
    val muted = Color(0xFF526773)
    val coral = Color(0xFFC84A3D)
}

@Composable
internal fun P011FlowHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        P011Stage("1  ENSIN", true, compact)
        Text("→", fontSize = 18.sp, color = P011Colors.muted)
        P011Stage("2  SITTEN", false, compact)
    }
}

@Composable
private fun P011Stage(
    label: String,
    selected: Boolean,
    compact: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (selected) P011Colors.teal else P011Colors.soft
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = 9.dp
            ),
            color = if (selected) Color.White else P011Colors.teal,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun P011Photo(
    name: String,
    imagePath: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imagePath) {
        if (imagePath.isBlank() || imagePath.startsWith("builtin:")) null else runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            var sample = 1
            while (
                bounds.outWidth / sample > 768 ||
                bounds.outHeight / sample > 768
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

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        val builtIn = AikaCardArt.imageRes(name, imagePath)
        if (builtIn != null) {
            Image(
                painter = painterResource(id = builtIn),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = modifier.background(Color(0xFFE6F4F1)),
                contentAlignment = Alignment.Center
            ) {
                Text("◇", fontSize = 50.sp, color = P011Colors.teal)
            }
        }
    }
}