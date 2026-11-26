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
import com.sc.biap.security.AppLock
import com.sc.biap.data.parseSpokenJob
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Mic
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.speech.RecognizerIntent
import android.content.Intent
import com.sc.biap.data.JOB_OVERDUE_DAYS
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import com.sc.biap.data.JobWork
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
    /** Open consignments of THIS size, so the sheet can say where the missing pieces are. */
    jobs: List<JobWork> = emptyList(),
    karigars: List<String> = emptyList(),
    activities: List<String> = emptyList(),
    onAddBatch: (number: Int, quantity: Int, price: Long, note: String) -> Unit,
    onSendOut: (karigar: String, activity: String, quantity: Int, note: String) -> Unit = { _, _, _, _ -> },
    onMakeMore: (karigar: String, activity: String, quantity: Int, note: String) -> Unit = { _, _, _, _ -> },
    onReceive: (jobId: String, quantity: Int) -> Unit = { _, _ -> },
    onCloseJob: (jobId: String) -> Unit = {},
    onResetSize: () -> Unit,
    onEditSizes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var makingMore by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf<JobWork?>(null) }
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
                val out = jobs.sumOf { it.outstanding }
                if (out > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Label("OUT")
                        Text(
                            "$out",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.tertiary,
                        )
                    }
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

            if (jobs.isNotEmpty()) {
                OutWithKarigars(
                    jobs = jobs,
                    onReceive = { receiving = it },
                    onClose = onCloseJob,
                )
            }

            BatchHistory(batches = batches, stock = inventory.stock)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add batch")
                }
                // MAKE MORE IS NEVER DISABLED, least of all at zero.
                //
                // Commissioning pieces takes nothing off the shelf - the karigar is being
                // given an instruction, not goods - so an empty size is exactly the one that
                // should be orderable. The old single "Send out" button got this backwards
                // and went grey precisely when it was most needed.
                OutlinedButton(
                    onClick = { makingMore = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Make more")
                }
            }

            // Handing real pieces over, which does need them to exist.
            TextButton(onClick = { sending = true }, enabled = inventory.stock > 0) {
                Text("Send these pieces for work")
            }

            TextButton(onClick = onEditSizes) { Text("Edit sizes") }

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

    if (makingMore) {
        MakeMoreDialog(
            size = inventory.size,
            inStock = inventory.stock,
            karigars = karigars,
            activities = activities,
            onConfirm = { karigar, activity, quantity, note ->
                onMakeMore(karigar, activity, quantity, note)
                makingMore = false
            },
            onDismiss = { makingMore = false },
        )
    }

    if (sending) {
        SendOutDialog(
            size = inventory.size,
            inStock = inventory.stock,
            karigars = karigars,
            activities = activities,
            onConfirm = { karigar, activity, quantity, note ->
                onSendOut(karigar, activity, quantity, note)
                sending = false
            },
            onDismiss = { sending = false },
        )
    }

    receiving?.let { job ->
        ReceiveBackDialog(
            job = job,
            onConfirm = { quantity ->
                onReceive(job.id, quantity)
                receiving = null
            },
            onDismiss = { receiving = null },
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

// #region 5. Job work

/**
 * What of this size is out, and with whom.
 *
 * Days out is shown on every line, not only the late ones, because the number the owner
 * wants is not "is this late" but "how long has he had them".
 */
@Composable
private fun OutWithKarigars(
    jobs: List<JobWork>,
    onReceive: (JobWork) -> Unit,
    onClose: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val now = remember { System.currentTimeMillis() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("OUT WITH KARIGARS")
        jobs.forEach { job ->
            val days = job.daysOut(now)
            val late = days >= JOB_OVERDUE_DAYS
            val ink = if (late) scheme.onErrorContainer else scheme.onSurface
            val quietInk = if (late) scheme.onErrorContainer else scheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (late) scheme.errorContainer else scheme.surfaceContainerHigh)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        job.karigar,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                    )
                    Text(
                        "${job.outstanding} out",
                        style = MaterialTheme.typography.titleSmall,
                        color = ink,
                    )
                }
                Text(
                    buildString {
                        append(job.activity)
                        append(" - ")
                        append(
                            when (days) {
                                0 -> "sent today"
                                1 -> "1 day"
                                else -> "$days days"
                            }
                        )
                        if (job.receivedQuantity > 0) {
                            append(" - ${job.receivedQuantity} of ${job.sentQuantity} back")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = quietInk,
                )
                if (job.note.isNotBlank()) {
                    Text(job.note, style = MaterialTheme.typography.bodySmall, color = quietInk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onReceive(job) }) { Text("Received") }
                    // Named for what it means rather than for what it does to a row: the
                    // pieces that never came back stay on the record as missing.
                    TextButton(onClick = { onClose(job.id) }) {
                        Text("Finish job", color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Sending pieces out. Names are picked from the Hub, never typed, so they stay one name. */
@Composable
private fun SendOutDialog(
    size: String,
    inStock: Int,
    karigars: List<String>,
    activities: List<String>,
    onConfirm: (karigar: String, activity: String, quantity: Int, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var karigar by remember { mutableStateOf(karigars.firstOrNull().orEmpty()) }
    var activity by remember { mutableStateOf(activities.firstOrNull().orEmpty()) }
    // Pre-filled with the whole stock, which is the usual case, and editable because it is
    // not the only one.
    var quantity by remember { mutableStateOf(inStock.toString()) }
    var note by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf<String?>(null) }

    // Only what the sentence actually said is filled in. A sentence that names no activity
    // leaves the activity alone rather than overwriting a correct choice with a guess.
    val dictate = rememberDictation(languageTag = "hi-IN") { spoken ->
        heard = spoken
        val parsed = parseSpokenJob(spoken, karigars, activities)
        parsed.karigar?.let { karigar = it }
        parsed.activity?.let { activity = it }
        parsed.quantity?.let { quantity = it.toString() }
    }

    val count = quantity.toIntOrNull() ?: 0
    val tooMany = count > inStock
    val valid = count > 0 && !tooMany && karigar.isNotBlank() && activity.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send size $size out") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "These come off the shelf and stay yours until they are back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                dictate?.let { speak ->
                    OutlinedButton(onClick = speak, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bol kar bhariye")
                    }
                    // Shown back so a misheard sentence is obvious at a glance rather than
                    // being discovered in the fields one at a time.
                    heard?.let {
                        Text(
                            "Heard: " + it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Label("TO")
                PickRow(options = karigars, selected = karigar, onSelect = { karigar = it })
                Label("FOR")
                PickRow(options = activities, selected = activity, onSelect = { activity = it })
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantity = it },
                    label = { Text("How many") },
                    supportingText = {
                        Text(if (tooMany) "Only $inStock in stock." else "$inStock in stock")
                    },
                    isError = tooMany,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 120) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(karigar, activity, count, note) }, enabled = valid) {
                Text("Send out")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Recording a return. Defaults to everything still out, since that is the usual return. */
@Composable
private fun ReceiveBackDialog(job: JobWork, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var quantity by remember { mutableStateOf(job.outstanding.toString()) }
    val count = quantity.toIntOrNull() ?: 0
    val tooMany = count > job.outstanding

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Back from ${job.karigar}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${job.outstanding} still out of the ${job.sentQuantity} sent. Put back only " +
                        "what has actually arrived - the rest stays out until it does.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantity = it },
                    label = { Text("How many came back") },
                    supportingText = { if (tooMany) Text("Only ${job.outstanding} are out.") },
                    isError = tooMany,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count) }, enabled = count > 0 && !tooMany) {
                Text("Put back in stock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A single-choice row of chips. Scrolls, because a workshop has more names than fit. */
@Composable
private fun PickRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) },
            )
        }
    }
}
// #endregion

// #region 6. Speaking instead of typing

/**
 * Dictate a sentence and fill in the form from it.
 *
 * Uses the phone's own recogniser rather than anything of ours: it is already installed,
 * already knows the owner's languages, and already shows a listening screen they recognise.
 *
 * It FILLS A FORM, it does not act. Whatever it mishears is one tap to correct and nothing
 * moves until the owner presses Send out. That is the entire reason it is allowed to guess.
 */
@Composable
private fun rememberDictation(
    languageTag: String,
    onHeard: (String) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) onHeard(spoken)
    }

    val intent = remember(languageTag) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            // The phone's own language is offered alongside, because a workshop sentence
            // mixes them: a Hindi sentence carrying an English word for the work.
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Kehiye")
        }
    }

    // No recogniser installed is a real state on a stripped-down phone. Returning null lets
    // the caller leave the button out rather than show one that does nothing.
    val available = remember {
        intent.resolveActivity(context.packageManager) != null
    }
    return if (!available) null else {
        {
            AppLock.beginTrustedFlow()
            runCatching { launcher.launch(intent) }
                .onFailure { AppLock.endTrustedFlow() }
            Unit
        }
    }
}
// #endregion

/**
 * Ordering more of a size.
 *
 * Deliberately says nothing about stock running out, because the pieces being ordered are not
 * coming off the shelf - it is an instruction to a karigar, and the shelf is untouched until
 * they arrive. The quantity is NOT pre-filled from stock for the same reason: what is there
 * now says nothing about how many to make.
 */
@Composable
private fun MakeMoreDialog(
    size: String,
    inStock: Int,
    karigars: List<String>,
    activities: List<String>,
    onConfirm: (karigar: String, activity: String, quantity: Int, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var karigar by remember { mutableStateOf(karigars.firstOrNull().orEmpty()) }
    var activity by remember { mutableStateOf(activities.firstOrNull().orEmpty()) }
    var quantity by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val count = quantity.toIntOrNull() ?: 0
    val valid = count > 0 && karigar.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make more of size $size") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (inStock == 0)
                        "This size is out of stock. Nothing comes off the shelf - these are " +
                            "being made, and they go into stock when they arrive."
                    else
                        "$inStock in stock now. These are additional, and go into stock when " +
                            "they arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Label("WHO IS MAKING THEM")
                PickRow(options = karigars, selected = karigar, onSelect = { karigar = it })
                Label("WHAT WORK")
                PickRow(options = activities, selected = activity, onSelect = { activity = it })
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) quantity = it },
                    label = { Text("How many to make") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 120) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(karigar, activity, count, note) }, enabled = valid) {
                Text("Order them")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
