package fi.aikapolku.app.link

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asetukset → Yksikön yhteys (laitekohtainen, V5a):
 *  - ohjaajan puhelin: vastaanotto päälle ja liitettyjen asiakaslaitteiden luettelo;
 *  - asiakkaan laite: liitä ohjaajan puhelimeen (haku samasta verkosta ja koodin vertailu).
 */
@Composable
internal fun LinkSettings() {
    val context = LocalContext.current
    val store = remember { LinkStore(context) }
    val revision = LinkStore.revision
    val identity = remember(revision) { store.identity }
    val peers = remember(revision) { store.peers() }
    var receiver by remember(revision) { mutableStateOf(store.receiverEnabled) }
    var name by remember(revision) { mutableStateOf(identity.name) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Text(stringResource(R.string.link_info), fontSize = 14.sp, color = AppColors.Muted)
    if (remember(revision) { store.peersLost }) {
        Text(stringResource(R.string.link_peers_lost), fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        label = { Text(stringResource(R.string.link_device_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (name.trim() != identity.name && name.isNotBlank()) {
        TextButton(onClick = { store.setName(name) }) { Text(stringResource(R.string.link_save_name)) }
    }

    // Paikan laite (A5): pitää paikan tiedot ja jakaa asukkaille heidän omansa.
    var place by remember(revision) { mutableStateOf(store.placeEnabled) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.link_place_label), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.link_place_info), fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(checked = place, onCheckedChange = { on ->
            place = on
            store.placeEnabled = on
            if (on) {
                receiver = true
                LinkReceiverService.start(context)
            }
        })
    }

    // Ohjaajan puhelin: vastaanotto.
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.link_receiver_label), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.link_receiver_info), fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(checked = receiver, enabled = !place, onCheckedChange = { on ->
            receiver = on
            store.receiverEnabled = on
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    fi.aikapolku.app.caregiver.CaregiverSession.keepOpenForOwnPicker()
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                LinkReceiverService.start(context)
            } else {
                LinkReceiverService.stop(context)
            }
        })
    }

    val senders = peers.filter { !it.receiver }
    val receivers = peers.filter { it.receiver }
    if (senders.isNotEmpty()) {
        Text(stringResource(R.string.link_senders), fontWeight = FontWeight.Bold)
        val seenRevision = LinkStore.seenRevision
        val profiles = remember(revision) { fi.aikapolku.app.data.ProfileStore.list(context) }
        senders.forEach { p ->
            val seen = remember(seenRevision, p.id) { store.lastSeen(p.id) }
            val seenInfo = if (seen <= 0L) stringResource(R.string.link_never_seen) else stringResource(R.string.link_last_seen, seenText(context, seen))
            val resident = profiles.firstOrNull { it.id == p.resident && p.resident.isNotBlank() }?.name
            // Paikan laite: onko asukkaan viimeisin muutos perillä hänen puhelimessaan (A5).
            val statusRevision = PlaceStatus.revision
            val delivery = if (resident != null && store.placeEnabled) remember(statusRevision, p.resident, fi.aikapolku.app.settings.SettingsRevision.value) {
                val d = PlaceStatus.delivered(context, p.resident)
                when {
                    d == null -> context.getString(R.string.place_not_delivered)
                    d.version == ResidentPackage.version(context, p.resident) -> context.getString(R.string.place_delivered, seenText(context, d.at))
                    else -> context.getString(R.string.place_waiting)
                }
            } else null
            val info = listOfNotNull(resident?.let { stringResource(R.string.link_resident_of, it) }, delivery, seenInfo).joinToString(" · ")
            PeerRow(p.name, info) { store.removePeer(p.id, receiver = false) }
        }
    }

    // Asiakkaan laite: liitä ohjaajan puhelimeen.
    Text(stringResource(R.string.link_receivers), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    if (receivers.isEmpty()) Text(stringResource(R.string.link_no_receivers), fontSize = 14.sp, color = AppColors.Muted)
    val syncRevision = PlaceSync.revision
    receivers.forEach { p ->
        val synced = remember(syncRevision, p.id) { PlaceSync.lastSync(context, p.id) }
        val info = if (!p.place) null
        else if (synced > 0L) stringResource(R.string.link_place_tag) + " · " + stringResource(R.string.place_synced, seenText(context, synced))
        else stringResource(R.string.link_place_tag)
        PeerRow(p.name, info) { store.removePeer(p.id, receiver = true) }
    }
    if (receivers.any { it.place }) {
        val scope = rememberCoroutineScope()
        TextButton(onClick = { scope.launch { PlaceSync.syncNow(context) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.place_sync_now))
        }
    }
    // Jonossa odottavat viestit (asiakkaan laite): lähtevät, kun yhteys palaa.
    val queueRevision = LinkOutbox.revision
    val queued = remember(queueRevision) { LinkOutbox.pending(context).size }
    if (queued > 0) {
        Text(stringResource(R.string.link_queue_count, queued), fontSize = 14.sp, color = AppColors.Coral)
        TextButton(onClick = { ClientMessages.resume(context) }) { Text(stringResource(R.string.link_queue_retry)) }
    }
    PairWithReceiver(identity)
}

@Composable
private fun PeerRow(name: String, info: String? = null, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            if (info != null) Text(info, fontSize = 13.sp, color = AppColors.Muted)
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.link_remove)) }
    }
}

/** Haku ja pariliitos asiakkaan laitteesta. */
@Composable
private fun PairWithReceiver(me: Identity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<FoundReceiver>?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }

    Button(
        onClick = {
            searching = true
            found = null
            status = ""
            scope.launch {
                found = LinkDiscovery.discover(context).filter { it.id != me.id }
                searching = false
            }
        },
        enabled = !searching && !pairing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
    ) { Text(stringResource(R.string.link_pair_button)) }
    if (searching) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.padding(4.dp))
            Text(stringResource(R.string.link_searching))
        }
    }
    found?.let { list ->
        if (list.isEmpty()) Text(stringResource(R.string.link_none_found), fontSize = 14.sp, color = AppColors.Muted)
        list.forEach { r ->
            TextButton(
                onClick = {
                    pairing = true
                    code = null
                    status = ""
                    found = null
                    scope.launch {
                        var saveFailed = false
                        val peer = withContext(Dispatchers.IO) {
                            runCatching { LinkProtocol.pair(r.host, r.port, me) { c -> code = c } }.getOrNull()
                                ?.takeIf { LinkStore(context).addPeer(it).also { ok -> saveFailed = !ok } }
                        }
                        if (saveFailed) {
                            status = context.getString(R.string.link_pair_save_failed)
                        } else if (peer != null) {
                            status = context.getString(R.string.link_paired, peer.name)
                            // Paikan laite: omat tiedot heti (A5).
                            if (peer.place) PlaceSync.start(context)
                        } else {
                            status = context.getString(R.string.link_pair_failed)
                        }
                        code = null
                        pairing = false
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(r.name.removePrefix("AikaPolku ")) }
        }
    }
    code?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, AppColors.Teal),
            colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.link_compare_code), fontWeight = FontWeight.Bold)
                Text(it, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text(stringResource(R.string.link_wait_accept), fontSize = 14.sp, color = AppColors.Muted)
            }
        }
    }
    if (status.isNotBlank()) Text(status, fontWeight = FontWeight.Bold, color = AppColors.Teal)
}

/** Ohjaajan puhelimessa: pariliitospyyntö näkyy missä tahansa näkymässä. */
@Composable
internal fun PairingRequestDialog() {
    val request = LinkPairing.pending ?: return
    val context = LocalContext.current
    // T4: paikan laite ilman näytön lukitusta ei hyväksy uusia liitoksia.
    if (request.place && !fi.aikapolku.app.caregiver.PrivacyPrefs.deviceSecure(context)) {
        androidx.compose.runtime.LaunchedEffect(request) { LinkPairing.answer(false) }
        return
    }
    // Paikan laitteella valitaan, kenen asukkaan puhelin liitetään (vain hänen tietonsa siirtyvät).
    val profiles = remember(request) { if (request.place) fi.aikapolku.app.data.ProfileStore.list(context).filter { it.name.isNotBlank() } else emptyList() }
    var resident by remember(request) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.link_pair_request_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.link_pair_request_text, request.name))
                Text(request.code, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text(stringResource(R.string.link_pair_request_check), fontSize = 14.sp, color = AppColors.Muted)
                if (request.place) {
                    Text(stringResource(R.string.link_pair_pick_resident), fontWeight = FontWeight.Bold)
                    profiles.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = resident == p.id, role = androidx.compose.ui.semantics.Role.RadioButton) { resident = p.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = resident == p.id, onClick = null)
                            Text(p.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { LinkPairing.answer(true, resident) },
                enabled = !request.place || resident.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
            ) {
                Text(stringResource(R.string.link_accept))
            }
        },
        dismissButton = { TextButton(onClick = { LinkPairing.answer(false) }) { Text(stringResource(R.string.link_decline)) } }
    )
}

/** Ohjaajan etusivulla: tänään tulleet asiakkaiden viestit (uusin ensin). */
@Composable
internal fun LinkInboxCard() {
    val context = LocalContext.current
    val messages = remember(LinkInbox.revision) { LinkInbox.today(context) }
    if (messages.isEmpty()) return
    val time = remember { DateTimeFormatter.ofPattern("H.mm") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, AppColors.Teal),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.link_inbox_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            messages.takeLast(5).reversed().forEach { m ->
                val at = Instant.ofEpochMilli(m.at).atZone(ZoneId.systemDefault()).toLocalTime().format(time)
                Text("$at  ${m.who.ifBlank { m.fromName }}: ${m.text}", fontSize = 16.sp, color = AppColors.Ink)
            }
            TextButton(onClick = { LinkInbox.clear(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.link_inbox_clear))
            }
        }
    }
}

/** "tänään 10.42" / "eilen 18.05" / "12.11. 9.30" */
internal fun seenText(context: android.content.Context, ms: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(ms).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val clock = "${t.hour}.${t.minute.toString().padStart(2, '0')}"
    return when (t.toLocalDate()) {
        today -> context.getString(R.string.link_seen_today, clock)
        today.minusDays(1) -> context.getString(R.string.link_seen_yesterday, clock)
        else -> "${t.dayOfMonth}.${t.monthValue}. $clock"
    }
}
