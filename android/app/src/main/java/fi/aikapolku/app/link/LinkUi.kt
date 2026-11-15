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

    // Ohjaajan puhelin: vastaanotto.
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.link_receiver_label), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.link_receiver_info), fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(checked = receiver, onCheckedChange = { on ->
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
        senders.forEach { p ->
            val seen = store.lastSeen(p.id)
            val info = if (seen <= 0L) stringResource(R.string.link_never_seen) else stringResource(R.string.link_last_seen, seenText(context, seen))
            PeerRow(p.name, info) { store.removePeer(p.id, receiver = false) }
        }
    }

    // Asiakkaan laite: liitä ohjaajan puhelimeen.
    Text(stringResource(R.string.link_receivers), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    if (receivers.isEmpty()) Text(stringResource(R.string.link_no_receivers), fontSize = 14.sp, color = AppColors.Muted)
    receivers.forEach { p -> PeerRow(p.name) { store.removePeer(p.id, receiver = true) } }
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
                        val peer = withContext(Dispatchers.IO) {
                            runCatching { LinkProtocol.pair(r.host, r.port, me) { c -> code = c } }.getOrNull()
                                ?.also { LinkStore(context).addPeer(it) }
                        }
                        if (peer != null) {
                            status = context.getString(R.string.link_paired, peer.name)
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
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.link_pair_request_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.link_pair_request_text, request.name))
                Text(request.code, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text(stringResource(R.string.link_pair_request_check), fontSize = 14.sp, color = AppColors.Muted)
            }
        },
        confirmButton = {
            Button(onClick = { LinkPairing.answer(true) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)) {
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
