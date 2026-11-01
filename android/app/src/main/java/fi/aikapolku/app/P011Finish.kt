package fi.aikapolku.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun P011LastActivityScreen(
    name: String,
    imagePath: String,
    onFinish: () -> Unit,
    /** null = piilotettu (ohjaajan lukitus päällä). */
    onAddNext: (() -> Unit)?
) {
    Spacer(Modifier.height(10.dp))

    P011HeroCard(
        label = "VIIMEINEN TOIMINTO",
        name = name,
        imagePath = imagePath,
        large = true
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (maxWidth >= 325.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = P011Colors.teal
                    )
                ) {
                    Text("✓ Valmis", fontSize = 15.sp)
                }
                if (onAddNext != null) {
                    OutlinedButton(
                        onClick = onAddNext,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) {
                        Text("+ Lisää vaihe", fontSize = 14.sp)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("✓ Valmis")
                }
                if (onAddNext != null) {
                    OutlinedButton(
                        onClick = onAddNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text("+ Lisää vaihe")
                    }
                }
            }
        }
    }
}

@Composable
internal fun P011DoneScreen(
    onHome: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp

    Spacer(
        Modifier.height(
            (screenHeight * 0.12f).dp
        )
    )

    Surface(
        modifier = Modifier.size(100.dp),
        shape = CircleShape,
        color = P011Colors.soft
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "✓",
                fontSize = 55.sp,
                color = P011Colors.teal
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Kaikki valmista!",
        modifier = Modifier.fillMaxWidth(),
        color = P011Colors.ink,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        "Ohjelma on päättynyt.",
        modifier = Modifier.fillMaxWidth(),
        fontSize = 16.sp,
        color = P011Colors.muted,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onHome,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = P011Colors.teal
        )
    ) {
        Text("Takaisin aloitukseen", fontSize = 17.sp)
    }
}