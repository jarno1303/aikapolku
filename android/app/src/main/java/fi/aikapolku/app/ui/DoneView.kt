package fi.aikapolku.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto

/**
 * Yhteinen "Kaikki valmista" -näkymä (päiväohjelma ja Ensin–Sitten): iso merkki, otsikko,
 * lyhyt teksti, tehdyt pikkukuvina ja yksi painike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllDoneView(
    subtitle: String,
    images: List<Pair<String, String>>,
    /** Himmennettävät kuvat (indeksit), esim. ohitetut tehtävät. */
    dimmed: Set<Int> = emptySet(),
    buttonText: String,
    onButton: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(104.dp).clip(CircleShape).background(AppColors.Teal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Text(
            stringResource(R.string.kaikki_valmista),
            modifier = Modifier.semantics { heading() },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        Text(subtitle, fontSize = 18.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
        if (images.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                images.forEachIndexed { index, (name, photo) ->
                    CardPhoto(
                        name,
                        photo,
                        Modifier.size(64.dp).alpha(if (index in dimmed) 0.3f else 1f).clip(RoundedCornerShape(14.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = buttonText, onClick = onButton)
    }
}
