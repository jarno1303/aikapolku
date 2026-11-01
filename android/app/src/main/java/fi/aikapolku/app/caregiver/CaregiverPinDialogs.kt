package fi.aikapolku.app.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import kotlinx.coroutines.delay

@Composable
internal fun CaregiverPinDialog(
    store: CaregiverLockStore,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onForgot: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(store.lockoutRemainingMs()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(lockoutMs > 0L) {
        while (store.lockoutRemainingMs() > 0L) {
            lockoutMs = store.lockoutRemainingMs()
            delay(1_000L)
        }
        lockoutMs = 0L
    }
    LaunchedEffect(lockoutMs == 0L) {
        if (lockoutMs == 0L) {
            delay(150L) // dialogin ikkunan on oltava liitetty ennen fokusointia
            runCatching { focus.requestFocus() }
        }
    }

    fun submit(value: String) {
        when (val result = store.verify(value)) {
            PinResult.Ok -> onSuccess()
            is PinResult.Wrong -> {
                pin = ""
                message = context.getString(R.string.vaara_pin_koodi_yrityksia_jaljella, result.attemptsLeft)
            }
            is PinResult.LockedOut -> {
                pin = ""
                message = null
                lockoutMs = result.remainingMs
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.caregiver_actions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.anna_ohjaajan_pin_koodi))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { typed ->
                        val digits = typed.filter { it.isDigit() }.take(CaregiverLockStore.PIN_LENGTH)
                        pin = digits
                        if (digits.length == CaregiverLockStore.PIN_LENGTH) submit(digits)
                    },
                    enabled = lockoutMs == 0L,
                    label = { Text(stringResource(R.string.pin_koodi)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                if (lockoutMs > 0L) {
                    val seconds = (lockoutMs + 999L) / 1000L
                    Text(
                        stringResource(R.string.liian_monta_vaaraa_yritysta_yrita, seconds),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onForgot) { Text(stringResource(R.string.unohditko_pin_koodin)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
internal fun CaregiverPinSetupDialog(
    changing: Boolean,
    deviceSecure: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val length = CaregiverLockStore.PIN_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (changing) stringResource(R.string.vaihda_pin_koodi) else stringResource(R.string.ota_ohjaajan_lukitus_kayttoon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.valitse_nelinumeroinen_pin_koodi_jota))
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text(stringResource(R.string.uusi_pin_koodi)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text(stringResource(R.string.pin_koodi_uudelleen)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!deviceSecure) {
                    Text(
                        stringResource(R.string.huom_puhelimessa_ei_ole_nayton),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = first.length == length && second.length == length,
                onClick = {
                    val problem: String? = CaregiverLockStore.pinProblem(first)?.let { context.getString(it.messageRes) }
                        ?: if (first != second) context.getString(R.string.pin_koodit_eivat_tasmaa) else null
                    if (problem != null) {
                        error = problem
                        second = ""
                    } else {
                        onSave(first)
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
