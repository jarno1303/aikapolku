package fi.aikapolku.app.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.rememberCachedImage
import fi.aikapolku.app.data.Profile
import fi.aikapolku.app.ui.AppColors

private val avatarColors = listOf(
    Color(0xFF0F766E), Color(0xFF6D4AA8), Color(0xFF1F6FA8),
    Color(0xFFB4532A), Color(0xFF3F7D3A), Color(0xFFA83E6B)
)

@Composable
internal fun profileName(profile: Profile): String =
    profile.name.ifBlank { stringResource(R.string.profile_unnamed) }

/** Profiilin kuva: oma valokuva tai nimen alkukirjain värillisessä ympyrässä. */
@Composable
internal fun ProfileAvatar(profile: Profile, size: Dp) {
    val photo = profile.photo.takeIf { it.isNotBlank() }
    val image = if (photo != null) rememberCachedImage(photo, null).image else null
    val modifier = Modifier.size(size).clip(CircleShape)
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        val color = avatarColors[Math.floorMod(profile.id.hashCode(), avatarColors.size)]
        Box(modifier = modifier.background(color), contentAlignment = Alignment.Center) {
            Text(
                profile.name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Aloitusnäkymän rivi: kuka käyttää nyt, ja vaihtopainike. Näkyy vain, kun profiileja on useampi. */
@Composable
internal fun CurrentProfileRow(profile: Profile, onSwitch: () -> Unit) {
    val name = profileName(profile)
    val a11y = stringResource(R.string.profile_switch_a11y, name)
    Surface(
        onClick = onSwitch,
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = a11y }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatar(profile, 44.dp)
            Text(
                name,
                modifier = Modifier.weight(1f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(stringResource(R.string.profile_switch), color = AppColors.Teal, fontSize = 16.sp)
        }
    }
}

/** Profiilin valinta: isot kuvat ja nimet, yksi napautus. */
@Composable
internal fun ProfilePickerDialog(
    profiles: List<Profile>,
    currentId: String,
    onPick: (Profile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_choose_title), fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val current = profile.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (current) AppColors.TealSoft else Color.Transparent)
                            .clickable(role = Role.Button) { onPick(profile) }
                            .semantics { selected = current }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ProfileAvatar(profile, 64.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profileName(profile), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                            if (current) {
                                Text(stringResource(R.string.profile_in_use), fontSize = 14.sp, color = AppColors.Teal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
