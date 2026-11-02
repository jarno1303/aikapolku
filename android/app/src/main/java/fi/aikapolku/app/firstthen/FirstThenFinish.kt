package fi.aikapolku.app.firstthen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import fi.aikapolku.app.ui.AllDoneView
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
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
        label = stringResource(R.string.now_label),
        name = name,
        imagePath = imagePath,
        large = true
    )

    // Valmis päätoimintona, "Lisää vaihe" toissijaisena (vain ohjaajalle).
    PrimaryButton(
        text = stringResource(R.string.flow_done_button),
        icon = Icons.Rounded.Check,
        onClick = onFinish
    )
    if (onAddNext != null) {
        SecondaryButton(
            text = stringResource(R.string.lisaa_vaihe),
            icon = Icons.Rounded.Add,
            onClick = onAddNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Ensin–Sitten tehty: yhteinen Kaikki valmista -näkymä. */
@Composable
internal fun FlowDoneScreen(
    onHome: () -> Unit
) {
    AllDoneView(
        subtitle = stringResource(R.string.on_paattynyt, stringResource(R.string.ensin_sitten)),
        images = emptyList(),
        buttonText = stringResource(R.string.takaisin_aloitukseen),
        onButton = onHome
    )
}
