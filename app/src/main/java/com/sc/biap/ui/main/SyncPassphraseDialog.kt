package com.sc.biap.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sc.biap.security.SyncPassphrase
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.titleLargeStrong

/**
 * @file SyncPassphraseDialog.kt
 * @description Asks for the passphrase that unwraps the sync key.
 *
 * Two different questions behind one dialog, because they are genuinely different moments:
 * choosing a passphrase for the first time carries a warning that losing it is permanent,
 * while entering an existing one does not need to be lectured at.
 */
@Composable
fun SyncPassphraseDialog(
    isNewPassphrase: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val tooShort = passphrase.length < SyncPassphrase.MIN_LENGTH
    val mismatch = isNewPassphrase && confirmation.isNotEmpty() && passphrase != confirmation
    val canConfirm = !tooShort && (!isNewPassphrase || passphrase == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNewPassphrase) "Create a sync passphrase" else "Enter your sync passphrase",
                style = MaterialTheme.typography.titleLargeStrong
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)) {
                Text(
                    if (isNewPassphrase) {
                        "This encrypts the key that protects your catalogue in Google Drive. " +
                            "Without it, anyone who reaches that folder can read everything."
                    } else {
                        "Needed to unlock the catalogue stored in Google Drive."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = AtelierShapes.CornerMedium,
                    isError = passphrase.isNotEmpty() && tooShort,
                    supportingText = if (passphrase.isNotEmpty() && tooShort) {
                        { Text("At least ${SyncPassphrase.MIN_LENGTH} characters") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isNewPassphrase) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = AtelierShapes.CornerMedium,
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text("The two do not match") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Said plainly and once. There is deliberately no recovery path: any
                    // way back in without the passphrase would be a second door, and would
                    // undo the entire point of moving the key out of plain text.
                    Text(
                        "If you forget this passphrase, the catalogue in Drive cannot be " +
                            "recovered. There is no reset. Write it down somewhere safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase.toCharArray()) },
                enabled = canConfirm
            ) {
                Text(if (isNewPassphrase) "Create & sync" else "Unlock & sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
