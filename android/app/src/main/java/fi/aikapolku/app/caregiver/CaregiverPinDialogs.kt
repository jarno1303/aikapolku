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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                message = "Väärä PIN-koodi. Yrityksiä jäljellä: ${result.attemptsLeft}."
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
        title = { Text("🔒 Ohjaajan toiminnot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Anna ohjaajan PIN-koodi.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { typed ->
                        val digits = typed.filter { it.isDigit() }.take(CaregiverLockStore.PIN_LENGTH)
                        pin = digits
                        if (digits.length == CaregiverLockStore.PIN_LENGTH) submit(digits)
                    },
                    enabled = lockoutMs == 0L,
                    label = { Text("PIN-koodi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                if (lockoutMs > 0L) {
                    val seconds = (lockoutMs + 999L) / 1000L
                    Text(
                        "Liian monta väärää yritystä. Yritä uudelleen ${seconds} sekunnin kuluttua.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onForgot) { Text("Unohditko PIN-koodin?") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Peruuta") }
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
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val length = CaregiverLockStore.PIN_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (changing) "Vaihda PIN-koodi" else "Ota ohjaajan lukitus käyttöön") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Valitse nelinumeroinen PIN-koodi, jota asiakas ei tiedä.")
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text("Uusi PIN-koodi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(length); error = null },
                    label = { Text("PIN-koodi uudelleen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!deviceSecure) {
                    Text(
                        "Huom: puhelimessa ei ole näytön lukitusta. Unohtunutta PIN-koodia ei voi " +
                            "nollata, ennen kuin puhelimen näytön lukitus otetaan käyttöön.",
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
                    val problem = CaregiverLockStore.pinProblem(first)
                        ?: if (first != second) "PIN-koodit eivät täsmää." else null
                    if (problem != null) {
                        error = problem
                        second = ""
                    } else {
                        onSave(first)
                    }
                }
            ) { Text("Tallenna") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Peruuta") }
        }
    )
}
