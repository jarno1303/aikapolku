package fi.aikapolku.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AikaColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F4F1),
    onPrimaryContainer = Color(0xFF145B55),
    secondary = Color(0xFF526773),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE8F8),
    onSecondaryContainer = Color(0xFF3A3459),
    tertiary = Color(0xFFC2413A),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF1F5F4),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF91AAA5),
    error = Color(0xFFB3261E)
)

/** Keep AikaPolku identity consistent; dynamic wallpaper colors are off. */
@Composable
fun AikapolkuTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AikaColors,
        typography = Typography,
        content = content
    )
}
