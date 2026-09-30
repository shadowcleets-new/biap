package com.sc.biap.ui.design

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.data.Batch
import com.sc.biap.data.SizeInventory
import com.sc.biap.ui.main.rupees
import com.sc.biap.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// #endregion

/**
 * @file SizeBatchSheet.kt
 * @description One size, its batch history, and the two things you can do to it.
 *
 * [TABLE OF CONTENTS]
 * 1. THE SHEET
 * 2. BATCH HISTORY
 * 3. ADDING A BATCH
 * 4. RESETTING
 *
 * WHY A SHEET RATHER THAN MORE OF THE DETAIL SCREEN:
 *   Batch history is per size, and the detail screen already shows every size at once. Laying
 *   the history under each row would multiply the longest screen in the app by five. The
 *   sheet is opened from the row it belongs to, so the context is never in doubt.
 *
 * WHY THE ROW'S OLD BEHAVIOUR IS STILL REACHABLE:
 *   Tapping a size used to jump straight into editing. That is now one button inside the
 *   sheet rather than the tap itself - the tap gained a destination, it did not lose one.
 */

private val DateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

// #region 1. The sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeBatchSheet(
    designName: String,
    inventory: SizeInventory,
    batches: List<Batch>,
    suggestedNumber: Int,
    onAddBatch: (number: Int, quantity: Int, price: Long, note: String) -> Unit,
    onResetSize: () -> Unit,
    onEditSizes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Size ${inventory.size}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                designName,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Label("IN STOCK")
                    Text(
                        "${inventory.stock}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (inventory.stock == 0) scheme.error else scheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("PRICE")
                    Text(
                        if (inventory.price > 0L) rupees(inventory.price) else "Not set",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            BatchHistory(batches = batches, stock = inventory.stock)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add batch")
                }
                OutlinedButton(onClick = onEditSizes, modifier = Modifier.weight(1f)) {
                    Text("Edit sizes")
                }
            }

            TextButton(
                onClick = { confirmingReset = true },
                enabled = inventory.stock > 0 || batches.any { it.remaining > 0 },
            ) {
                Text("Reset this size to 0", color = scheme.error)
            }
        }
    }

    if (adding) {
        AddBatchDialog(
            size = inventory.size,
            suggestedNumber = suggestedNumber,
            suggestedPrice = inventory.price,
            onConfirm = { number, quantity, price, note ->
                onAddBatch(number, quantity, price, note)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    if (confirmingReset) {
        ResetConfirmation(
            title = "Reset size ${inventory.size} to 0?",
            body = "The ${inventory.stock} pieces in stock are cleared. The price, the " +
                "karigars and every other detail stay exactly as they are, and the batch " +
                "history is kept - each batch just shows nothing left.",
            onConfirm = { onResetSize(); confirmingReset = false; onDismiss() },
            onDismiss = { confirmingReset = false },
        )
    }
}

// #endregion

// #region 2. Batch history

@Composable
private fun BatchHistory(batches: List<Batch>, stock: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("BATCHES")
        if (batches.isEmpty()) {
            // Said plainly rather than shown as an empty list, because "no batches" and "no
            // stock" are different facts and this size may well have plenty of stock.
            Text(
                if (stock > 0)
                    "No batches recorded yet. This size has $stock in stock from before " +
                        "batches were tracked - the next one you add starts the history."
                else "No batches recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            return@Column
        }

        batches.forEach { batch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (batch.isSpent) scheme.surfaceContainerLow else scheme.surfaceContainer
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (batch.isSpent) scheme.surfaceContainerHighest else scheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "#${batch.number}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (batch.isSpent) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (batch.isSpent) "${batch.quantity} made, none left"
                        else "${batch.remaining} left of ${batch.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        DateFormat.format(Date(batch.createdAt)) +
                            (if (batch.note.isNotBlank()) " · ${batch.note}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (batch.price > 0L) {
                    Text(
                        rupees(batch.price),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.1.sp,
    )
}

// #endregion

// #region 3. Adding a batch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBatchDialog(
    size: String,
    suggestedNumber: Int,
    suggestedPrice: Long,
    onConfirm: (number: Int, quantity: Int, price: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Pre-filled, not fixed. The suggestion is one past the highest number this size has
    // used; typing over it is the manual override, which is why this is a text field
    // rather than a label.
    var number by remember { mutableStateOf(suggestedNumber.toString()) }
    var quantity by remember { mutableStateOf("") }
    var price by remember {
        mutableStateOf(if (suggestedPrice > 0L) FileUtils.formatPrice(suggestedPrice) else "")
    }
    var note by remember { mutableStateOf("") }

    val parsedNumber = number.trim().toIntOrNull()
    val parsedQuantity = quantity.trim().toIntOrNull()
    val parsedPrice = FileUtils.parsePriceToCents(price)
    val valid = parsedNumber != null && parsedNumber > 0 &&
        parsedQuantity != null && parsedQuantity > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add batch for size $size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { if (it.length <= 6) number = it.filter(Char::isDigit) },
                        label = { Text("Batch no.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { if (it.length <= 6) quantity = it.filter(Char::isDigit) },
                        label = { Text("Pieces") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price per piece") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 60) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "These pieces are added to stock for size $size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(parsedNumber ?: 0, parsedQuantity ?: 0, parsedPrice, note.trim())
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// #endregion

// #region 4. Resetting

/**
 * Shared by the single-size and the whole-design reset.
 *
 * Both spell out what is NOT touched. "Reset stock" sounds like it could mean the design,
 * and the whole point of the feature is that everything except the number survives.
 */
@Composable
fun ResetConfirmation(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// #endregion
