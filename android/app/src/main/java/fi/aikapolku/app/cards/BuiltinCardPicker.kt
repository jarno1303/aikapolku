package fi.aikapolku.app.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R

@Composable
internal fun BuiltinCardPicker(
    onDismiss: () -> Unit,
    /** Valittu kortti: tunnus ja nimi sovelluksen nykyisellä kielellä. */
    onSelect: (id: String, name: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.valitse_valmis_kuva)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CardArt.templates.forEach { template ->
                    val name = stringResource(template.nameRes)
                    TextButton(
                        onClick = { onSelect(template.id, name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CardPhoto(
                                name = name,
                                imagePath = "builtin:${template.id}",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(name, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.peruuta)) }
        }
    )
}
