package fi.aikapolku.app.firstthen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.FlowColors

@Composable
internal fun LastActivityScreen(
    name: String,
    imagePath: String,
    onFinish: () -> Unit,
    /** null = piilotettu (ohjaajan lukitus päällä). */
    onAddNext: (() -> Unit)?
) {
    Spacer(Modifier.height(10.dp))

    HeroCard(
        label = stringResource(R.string.viimeinen_toiminto),
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
                        containerColor = FlowColors.teal
                    )
                ) {
                    Text(stringResource(R.string.flow_done_button), fontSize = 15.sp)
                }
                if (onAddNext != null) {
                    OutlinedButton(
                        onClick = onAddNext,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) {
                        Text(stringResource(R.string.lisaa_vaihe), fontSize = 14.sp)
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
                    Text(stringResource(R.string.flow_done_button))
                }
                if (onAddNext != null) {
                    OutlinedButton(
                        onClick = onAddNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text(stringResource(R.string.lisaa_vaihe))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FlowDoneScreen(
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
        color = FlowColors.soft
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "✓",
                fontSize = 55.sp,
                color = FlowColors.teal
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        stringResource(R.string.kaikki_valmista),
        modifier = Modifier.fillMaxWidth(),
        color = FlowColors.ink,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        stringResource(R.string.ohjelma_on_paattynyt),
        modifier = Modifier.fillMaxWidth(),
        fontSize = 16.sp,
        color = FlowColors.muted,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onHome,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FlowColors.teal
        )
    ) {
        Text(stringResource(R.string.takaisin_aloitukseen), fontSize = 17.sp)
    }
}
