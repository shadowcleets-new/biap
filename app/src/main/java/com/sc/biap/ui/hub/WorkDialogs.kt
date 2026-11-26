package com.sc.biap.ui.hub

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sc.biap.data.Design
import com.sc.biap.data.WorkKind
import com.sc.biap.data.WorkLot
import com.sc.biap.data.label
import com.sc.biap.ui.components.MediaAttachments
import com.sc.biap.ui.main.DesignThumbnail
// #endregion

/**
 * @file WorkDialogs.kt
 * @description Putting work in, and taking it back out.
 *
 * THE ADDING FLOW IS A QUESTION, NOT A FORM:
 *   It opens by asking which of three things is happening, in the workshop's own words -
 *   a new design, making more, or pieces to be fixed - and only then shows the fields that
 *   question needs. A single form carrying every field for all three would make everybody
 *   read past two thirds of it every time, and it is the people who do not use the app all
 *   day who would pay for that.
 */

// #region Adding

private enum class AddStep { CHOOSE, SAMPLE, PRODUCTION, ALTERATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddWorkSheet(
    state: WorkUiState,
    onDismiss: () -> Unit,
    onSample: (
        name: String, karigar: String, activity: String, note: String,
        photo: String?, voice: String?,
    ) -> Unit,
    onProduction: (
        designId: String, karigar: String, activity: String,
        quantities: Map<String, Int>, batchNumber: Int, note: String,
    ) -> Unit,
    onAlteration: (
        designId: String, size: String, karigar: String, qty: Int, note: String,
        photo: String?, voice: String?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(AddStep.CHOOSE) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                AddStep.CHOOSE -> {
                    Text(
                        "What is happening?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ChoiceRow(
                        icon = Icons.Default.Lightbulb,
                        title = "A new design",
                        body = "A karigar is making one sample for you to look at.",
                        onClick = { step = AddStep.SAMPLE },
                    )
                    ChoiceRow(
                        icon = Icons.Default.LocalShipping,
                        title = "Make more of a design",
                        body = "A run of pieces for something already in your catalogue.",
                        onClick = { step = AddStep.PRODUCTION },
                    )
                    ChoiceRow(
                        icon = Icons.Default.Build,
                        title = "Pieces to be fixed",
                        body = "A few pieces going back to be altered or replaced.",
                        onClick = { step = AddStep.ALTERATION },
                    )
                }

                AddStep.SAMPLE -> SampleForm(state, onSample)
                AddStep.PRODUCTION -> ProductionForm(state, onProduction)
                AddStep.ALTERATION -> AlterationForm(state, onAlteration)
            }
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SampleForm(
    state: WorkUiState,
    onConfirm: (
        name: String, karigar: String, activity: String, note: String,
        photo: String?, voice: String?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf<String?>(null) }
    var karigar by remember { mutableStateOf(state.karigars.firstOrNull()?.name.orEmpty()) }
    var activity by remember { mutableStateOf(state.activities.firstOrNull().orEmpty()) }
    var note by remember { mutableStateOf("") }

    FormTitle("A new design")
    Text(
        "It is not added to your catalogue yet. When the sample comes back and you approve " +
            "it, it becomes a real design.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { if (it.length <= 80) name = it },
        label = { Text("What shall we call it?") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Label("WHO IS MAKING IT")
    NamePicker(state.karigars.map { it.name }, karigar) { karigar = it }
    Label("WHAT WORK")
    NamePicker(state.activities, activity) { activity = it }
    OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 200) note = it },
        label = { Text("Note (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Label("A PICTURE OR A SPOKEN NOTE")
    MediaAttachments(
        photoPath = photo,
        voicePath = voice,
        onPhotoChange = { photo = it },
        onVoiceChange = { voice = it },
    )
    ConfirmButton(
        text = "Ask for the sample",
        enabled = name.isNotBlank() && karigar.isNotBlank(),
        onClick = { onConfirm(name, karigar, activity, note, photo, voice) },
    )
}

@Composable
private fun ProductionForm(
    state: WorkUiState,
    onConfirm: (
        designId: String, karigar: String, activity: String,
        quantities: Map<String, Int>, batchNumber: Int, note: String,
    ) -> Unit,
) {
    var design by remember { mutableStateOf<Design?>(null) }
    var karigar by remember { mutableStateOf(state.karigars.firstOrNull()?.name.orEmpty()) }
    var activity by remember { mutableStateOf(state.activities.firstOrNull().orEmpty()) }
    var note by remember { mutableStateOf("") }
    val quantities = remember { mutableStateMapOf<String, String>() }

    FormTitle("Make more")
    val chosen = design
    if (chosen == null) {
        Text(
            "Which design?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DesignPicker(state.designsById.values.toList()) { design = it }
        return
    }

    DesignChosen(chosen) { design = null }

    // Pre-filled from how the sizes actually sold. The owner edits or accepts; either way
    // they are not starting from five of everything.
    LaunchedEffect(chosen.id) {
        quantities.clear()
        chosen.sizeInventories.forEach { quantities[it.size] = "" }
    }

    Label("HOW MANY OF EACH SIZE")
    chosen.sizeInventories.forEach { inv ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                inv.size,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(56.dp),
            )
            Text(
                "${inv.stock} in stock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = quantities[inv.size].orEmpty(),
                onValueChange = {
                    if (it.length <= 4 && it.all(Char::isDigit)) quantities[inv.size] = it
                },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
        }
    }

    Label("WHO IS MAKING THEM")
    NamePicker(state.karigars.map { it.name }, karigar) { karigar = it }
    Label("WHAT WORK")
    NamePicker(state.activities, activity) { activity = it }
    OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 200) note = it },
        label = { Text("Note (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    val parsed = quantities.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }
    ConfirmButton(
        text = if (parsed.isEmpty()) "Enter quantities" else "Order ${parsed.values.sum()} pieces",
        enabled = parsed.isNotEmpty() && karigar.isNotBlank(),
        onClick = { onConfirm(chosen.id, karigar, activity, parsed, 0, note) },
    )
}

@Composable
private fun AlterationForm(
    state: WorkUiState,
    onConfirm: (
        designId: String, size: String, karigar: String, qty: Int, note: String,
        photo: String?, voice: String?,
    ) -> Unit,
) {
    var design by remember { mutableStateOf<Design?>(null) }
    var photo by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf("") }
    var karigar by remember { mutableStateOf(state.karigars.firstOrNull()?.name.orEmpty()) }
    var qty by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }

    FormTitle("Pieces to be fixed")
    val chosen = design
    if (chosen == null) {
        Text(
            "Which design?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DesignPicker(state.designsById.values.toList()) {
            design = it
            size = it.sizeInventories.firstOrNull { inv -> inv.stock > 0 }?.size.orEmpty()
        }
        return
    }

    DesignChosen(chosen) { design = null }

    Label("WHICH SIZE")
    NamePicker(chosen.sizeInventories.map { it.size }, size) { size = it }
    val inStock = chosen.sizeInventories.firstOrNull { it.size == size }?.stock ?: 0

    Label("WHO IS FIXING THEM")
    NamePicker(state.karigars.map { it.name }, karigar) { karigar = it }

    val count = qty.toIntOrNull() ?: 0
    val tooMany = count > inStock
    OutlinedTextField(
        value = qty,
        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) qty = it },
        label = { Text("How many pieces") },
        supportingText = {
            Text(if (tooMany) "Only $inStock in stock for $size." else "$inStock in stock")
        },
        isError = tooMany,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 200) note = it },
        label = { Text("What is wrong with them?") },
        modifier = Modifier.fillMaxWidth(),
    )
    // The reason this screen exists. A photograph of the fault and four spoken seconds
    // about it beat any field asking somebody to write it down, and are far likelier to
    // actually get filled in.
    Label("SHOW WHAT IS WRONG")
    MediaAttachments(
        photoPath = photo,
        voicePath = voice,
        onPhotoChange = { photo = it },
        onVoiceChange = { voice = it },
    )
    ConfirmButton(
        text = "Send for alteration",
        enabled = count > 0 && !tooMany && size.isNotBlank() && karigar.isNotBlank(),
        onClick = { onConfirm(chosen.id, size, karigar, count, note, photo, voice) },
    )
}
// #endregion

// #region Receiving and finishing

/**
 * Taking a lot back.
 *
 * Whole-lot first, because that is how most of them arrive and making the common case one
 * tap is worth more than making the rare one short. Sizes are listed underneath for the
 * delivery that comes in parts.
 */
@Composable
internal fun ReceiveLotDialog(
    lot: WorkLot,
    onConfirmWhole: () -> Unit,
    onConfirmRow: (rowId: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Back from ${lot.karigar}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (lot.kind == WorkKind.PRODUCTION)
                        "These go into stock as a new batch."
                    else
                        "These go back into stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (lot.rows.size > 1) {
                    Text(
                        "If only some sizes have come, tap just those.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    lot.rows.filter { it.outstanding > 0 }.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onConfirmRow(row.id, row.outstanding) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.size, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${row.outstanding} still out",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmWhole) { Text("All ${lot.outstanding} came back") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Finishing a lot, with the missing pieces named rather than quietly dropped. */
@Composable
internal fun FinishLotDialog(lot: WorkLot, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish this ${lot.kind.label.lowercase()}?") },
        text = {
            Text(
                if (lot.outstanding > 0)
                    "${lot.outstanding} of ${lot.sent} never came back. They are NOT put into " +
                        "stock - they stay on the record as missing, so you can see it later."
                else
                    "Everything came back. This just closes the job.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
// #endregion

// #region Small pieces

@Composable
private fun FormTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Picked, never typed, so names stay the one name the Hub made them. */
@Composable
private fun NamePicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
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

@Composable
private fun DesignPicker(designs: List<Design>, onPick: (Design) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        designs.filter { it.archivedAt == 0L }.sortedBy { it.name.lowercase() }.forEach { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(d) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { DesignThumbnail(d, 44.dp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${d.sizeInventories.sumOf { it.stock }} in stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignChosen(design: Design, onChange: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) { DesignThumbnail(design, 40.dp) }
        Spacer(Modifier.width(12.dp))
        Text(design.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onChange) { Text("Change") }
    }
}

@Composable
private fun ConfirmButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text) }
}
// #endregion
