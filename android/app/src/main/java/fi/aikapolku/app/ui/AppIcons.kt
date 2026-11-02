package fi.aikapolku.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Sovelluksen omat kuvakkeet niille, joita Androidin peruskuvakesarjassa (Icons.Rounded)
 * ei ole. Sama 24 × 24 -ruudukko ja viivapaino kuin peruskuvakkeissa, joten ne sopivat
 * yhteen. Väri tulee Icon-komponentin tint-arvosta.
 */
internal object AppIcons {
    private fun stroked(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()

    /** Kamera (oma valokuva). */
    val Camera: ImageVector by lazy {
        stroked("Camera") {
            moveTo(4f, 8f)
            lineTo(7f, 8f)
            lineTo(8.5f, 5.5f)
            lineTo(15.5f, 5.5f)
            lineTo(17f, 8f)
            lineTo(20f, 8f)
            quadTo(21f, 8f, 21f, 9f)
            lineTo(21f, 18f)
            quadTo(21f, 19f, 20f, 19f)
            lineTo(4f, 19f)
            quadTo(3f, 19f, 3f, 18f)
            lineTo(3f, 9f)
            quadTo(3f, 8f, 4f, 8f)
            close()
            // Linssi
            moveTo(12f, 10f)
            arcToRelative(3.2f, 3.2f, 0f, true, true, 0f, 6.4f)
            arcToRelative(3.2f, 3.2f, 0f, true, true, 0f, -6.4f)
        }
    }

    /** Kuva (valmis kuva / kuvagalleria). */
    val Image: ImageVector by lazy {
        stroked("Image") {
            moveTo(5f, 4f)
            lineTo(19f, 4f)
            quadTo(20f, 4f, 20f, 5f)
            lineTo(20f, 19f)
            quadTo(20f, 20f, 19f, 20f)
            lineTo(5f, 20f)
            quadTo(4f, 20f, 4f, 19f)
            lineTo(4f, 5f)
            quadTo(4f, 4f, 5f, 4f)
            close()
            moveTo(4.5f, 17f)
            lineTo(9f, 12f)
            lineTo(13f, 16f)
            lineTo(15.5f, 13.5f)
            lineTo(19.5f, 17.5f)
            moveTo(15.5f, 7.5f)
            arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 3f)
            arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, -3f)
        }
    }

    /** Kaiutin (toista puhe tai ääni). */
    val Speaker: ImageVector by lazy {
        stroked("Speaker") {
            moveTo(4f, 9.5f)
            lineTo(7.5f, 9.5f)
            lineTo(12f, 5.5f)
            lineTo(12f, 18.5f)
            lineTo(7.5f, 14.5f)
            lineTo(4f, 14.5f)
            close()
            moveTo(15.5f, 9f)
            quadTo(17.5f, 12f, 15.5f, 15f)
            moveTo(18f, 6.5f)
            quadTo(22f, 12f, 18f, 17.5f)
        }
    }

    /** Mikrofoni (äänitä oma viesti). */
    val Mic: ImageVector by lazy {
        stroked("Mic") {
            moveTo(12f, 3.5f)
            quadTo(14.5f, 3.5f, 14.5f, 6f)
            lineTo(14.5f, 11.5f)
            quadTo(14.5f, 14f, 12f, 14f)
            quadTo(9.5f, 14f, 9.5f, 11.5f)
            lineTo(9.5f, 6f)
            quadTo(9.5f, 3.5f, 12f, 3.5f)
            close()
            moveTo(6f, 11f)
            quadTo(6f, 17f, 12f, 17f)
            quadTo(18f, 17f, 18f, 11f)
            moveTo(12f, 17f)
            lineTo(12f, 20.5f)
        }
    }

    /** Tauko (kaksi pystyviivaa). */
    val Pause: ImageVector by lazy {
        ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(7f, 19f); close()
                moveTo(14f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(14f, 19f); close()
            }
        }.build()
    }

    /** Lukko auki (ohjaajan toiminnot avattu). */
    val LockOpen: ImageVector by lazy {
        stroked("LockOpen") {
            moveTo(6f, 11f)
            lineTo(18f, 11f)
            quadTo(19f, 11f, 19f, 12f)
            lineTo(19f, 19f)
            quadTo(19f, 20f, 18f, 20f)
            lineTo(6f, 20f)
            quadTo(5f, 20f, 5f, 19f)
            lineTo(5f, 12f)
            quadTo(5f, 11f, 6f, 11f)
            close()
            moveTo(8f, 11f)
            lineTo(8f, 7.5f)
            quadTo(8f, 3.5f, 12f, 3.5f)
            quadTo(15.2f, 3.5f, 15.8f, 6.5f)
        }
    }

    /** Siirtokahva (kolme vaakaviivaa). */
    val DragHandle: ImageVector by lazy {
        stroked("DragHandle") {
            moveTo(5f, 8f); lineTo(19f, 8f)
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(5f, 16f); lineTo(19f, 16f)
        }
    }
}
