package com.sc.biap.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sc.biap.security.SyncPassphrase

/**
 * @file PassphraseDialogs.kt
 * @description Changing the backup passphrase, and starting over when it is gone.
 *
 * THE TWO ARE DELIBERATELY NOT THE SAME DIALOG:
 *   Change re-wraps the existing key, so the backup keeps working and every other device
 *   keeps working. Reset throws the old key away because nobody can open it any more, and
 *   replaces the backup with a fresh copy from this phone. One is routine; the other cannot
 *   be undone. Collapsing them into a single screen with a checkbox would make the
 *   difference something you skim past.
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangePassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (current: CharArray, new: CharArray) -> Unit,
    error: String? = null,
    busy: Boolean = false,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val mismatch = confirm.isNotEmpty() && next != confirm
    val tooShort = next.isNotEmpty() && next.length < SyncPassphrase.MIN_LENGTH
    val valid = current.isNotEmpty() && next.length >= SyncPassphrase.MIN_LENGTH && next == confirm

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your backup stays readable - only the passphrase protecting it changes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PassphraseField(current, { current = it }, "Current passphrase")
                PassphraseField(next, { next = it }, "New passphrase")
                PassphraseField(confirm, { confirm = it }, "Repeat new passphrase")

                val message = when {
                    error != null -> error
                    tooShort -> "Use at least ${SyncPassphrase.MIN_LENGTH} characters."
                    mismatch -> "The two new passphrases do not match."
                    else -> null
                }
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = valid && !busy,
                onClick = { onConfirm(current.toCharArray(), next.toCharArray()) },
            ) { Text(if (busy) "Saving…" else "Change") }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Starting over. The wording carries the whole weight here.
 *
 * It states what is actually lost (the old backup, which nobody could read anyway) and what
 * is not (the designs on this phone, which become the new backup). [designCount] is shown
 * because "12 designs will become your new backup" is concrete in a way that "your data is
 * safe" is not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResetPassphraseDialog(
    designCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (new: CharArray) -> Unit,
    error: String? = null,
    busy: Boolean = false,
) {
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val nothingToBackUp = designCount == 0
    val valid = !nothingToBackUp &&
        next.length >= SyncPassphrase.MIN_LENGTH && next == confirm

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (nothingToBackUp) "Nothing to back up" else "Start the backup over") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nothingToBackUp) {
                    Text(
                        "There are no designs on this phone, so there is nothing to make a " +
                            "new backup from. If another device still has your catalogue, " +
                            "start there instead - resetting from here would leave you with " +
                            "an empty backup and no way back.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "A forgotten passphrase cannot be recovered - the backup in Drive is " +
                            "encrypted with it and cannot be opened without it, by anyone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "This replaces that unreadable backup with a fresh one made from the " +
                            "$designCount ${if (designCount == 1) "design" else "designs"} on " +
                            "this phone. Your designs are not affected. Other devices will " +
                            "need the new passphrase, and will merge their designs back in " +
                            "when they next sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PassphraseField(next, { next = it }, "New passphrase")
                    PassphraseField(confirm, { confirm = it }, "Repeat new passphrase")

                    val message = when {
                        error != null -> error
                        next.isNotEmpty() && next.length < SyncPassphrase.MIN_LENGTH ->
                            "Use at least ${SyncPassphrase.MIN_LENGTH} characters."
                        confirm.isNotEmpty() && next != confirm ->
                            "The two passphrases do not match."
                        else -> null
                    }
                    if (message != null) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!nothingToBackUp) {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    enabled = valid && !busy,
                    onClick = { onConfirm(next.toCharArray()) },
                ) { Text(if (busy) "Replacing…" else "Replace backup") }
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = onDismiss) {
                Text(if (nothingToBackUp) "Close" else "Cancel")
            }
        },
    )
}

@Composable
private fun PassphraseField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
