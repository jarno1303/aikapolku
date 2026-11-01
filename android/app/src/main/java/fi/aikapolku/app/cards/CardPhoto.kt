package fi.aikapolku.app.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.ui.FlowColors

@Composable
internal fun CardPhoto(
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
        val builtIn = CardArt.imageRes(name, imagePath)
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
                Text("◇", fontSize = 50.sp, color = FlowColors.teal)
            }
        }
    }
}
