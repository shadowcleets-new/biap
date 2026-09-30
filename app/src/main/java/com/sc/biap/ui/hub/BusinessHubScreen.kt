package com.sc.biap.ui.hub

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.Karigar
import kotlin.math.roundToInt
// #endregion

/**
 * @file BusinessHubScreen.kt
 * @description Who works here, and what each of them does - assigned by dragging a name.
 *
 * [TABLE OF CONTENTS]
 * 1. SCREEN
 * 2. THE BOARD
 * 3. CHIPS AND DRAGGING
 * 4. DIALOGS
 *
 * WHY DRAGGING RATHER THAN A DROPDOWN PER ACTIVITY:
 *   The owner asked for it, and the reason holds up: assigning eleven people across five
 *   activities through dropdowns is fifty-five decisions read one at a time, where the board
 *   shows the whole allocation at once and changing it is one gesture. The dropdown is still
 *   there in effect - tapping a name in the pool offers the same assignment - because a drag
 *   is the fast path, not the only path.
 *
 * WHY LONG-PRESS TO START A DRAG:
 *   The board scrolls. A drag that began on touch-down would fight the scroll on every
 *   flick. Long-press is the gesture Android already uses to mean "pick this up".
 */

private val ChipRadius = 20.dp
private val DropZoneMinHeight = 72.dp

// #region 1. Screen

@Composable
fun defaultBusinessHubViewModel(): BusinessHubViewModel {
    val context = LocalContext.current
    return viewModel { BusinessHubViewModel(DefaultDataRepository.getInstance(context)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BusinessHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessHubViewModel = defaultBusinessHubViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // --- drag state, owned here because the pool and the zones both need it ---
    var dragging by remember { mutableStateOf<Karigar?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    // Activity name to where its card sits on screen. Recorded as the cards lay out, so the
    // drop test is a plain hit test rather than anything the zones have to coordinate.
    val dropZones = remember { mutableStateMapOf<String, Rect>() }
    var hovered by remember { mutableStateOf<String?>(null) }

    var addKarigarOpen by remember { mutableStateOf(false) }
    var addActivityOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Karigar?>(null) }
    var removing by remember { mutableStateOf<Karigar?>(null) }
    var removingActivity by remember { mutableStateOf<String?>(null) }

    fun zoneAt(point: Offset): String? =
        dropZones.entries.firstOrNull { it.value.contains(point) }?.key

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text("Business Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "pool") {
                    KarigarPool(
                        karigars = state.karigars,
                        dragging = dragging,
                        onDragStart = { karigar, origin ->
                            dragging = karigar
                            dragPosition = origin
                        },
                        onDrag = { delta ->
                            dragPosition += delta
                            hovered = zoneAt(dragPosition)
                        },
                        onDragEnd = {
                            val target = zoneAt(dragPosition)
                            val picked = dragging
                            if (target != null && picked != null) viewModel.assign(picked.id, target)
                            dragging = null
                            hovered = null
                        },
                        onDragCancel = { dragging = null; hovered = null },
                        onRename = { renaming = it },
                        onRemove = { removing = it },
                        onAdd = { addKarigarOpen = true },
                    )
                }

                item(key = "activities-heading") {
                    SectionHeading(
                        title = "Activities",
                        hint = "Hold a name above, then drag it onto the work they do.",
                        onAdd = { addActivityOpen = true },
                    )
                }

                items(state.activities.size, key = { state.activities[it] }) { index ->
                    val activity = state.activities[index]
                    ActivityDropZone(
                        activity = activity,
                        assigned = state.assignedBy[activity].orEmpty(),
                        isHovered = hovered == activity,
                        isDragging = dragging != null,
                        onBounds = { dropZones[activity] = it },
                        onUnassign = { karigar -> viewModel.unassign(karigar.id, activity) },
                        onRemoveActivity = { removingActivity = activity },
                    )
                }

                if (state.activities.isEmpty()) {
                    item(key = "no-activities") {
                        Text(
                            "No activities yet. Add one to start assigning work.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }

            // The chip that follows the finger. Drawn last so it floats over the board, and
            // offset by half its own height so it sits under the fingertip rather than
            // beneath it where the finger would cover it.
            dragging?.let { karigar ->
                val density = LocalDensity.current
                Surface(
                    shape = RoundedCornerShape(ChipRadius),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier.offset {
                        IntOffset(
                            (dragPosition.x - with(density) { 48.dp.toPx() }).roundToInt(),
                            (dragPosition.y - with(density) { 20.dp.toPx() }).roundToInt(),
                        )
                    },
                ) {
                    Text(
                        karigar.name,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    // #region 4. Dialogs

    if (addKarigarOpen) {
        NameDialog(
            title = "Add karigar",
            label = "Name",
            confirmLabel = "Add",
            onConfirm = { viewModel.addKarigar(it); addKarigarOpen = false },
            onDismiss = { addKarigarOpen = false },
        )
    }

    if (addActivityOpen) {
        NameDialog(
            title = "Add activity",
            label = "Activity",
            confirmLabel = "Add",
            onConfirm = { viewModel.addActivity(it); addActivityOpen = false },
            onDismiss = { addActivityOpen = false },
        )
    }

    renaming?.let { karigar ->
        NameDialog(
            title = "Rename karigar",
            label = "Name",
            confirmLabel = "Save",
            initial = karigar.name,
            onConfirm = { viewModel.renameKarigar(karigar.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    removing?.let { karigar ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${karigar.name}?") },
            // Says what it does NOT touch, because removing someone from the Hub sounds
            // like it might rewrite the designs they are named on. It does not.
            text = {
                Text(
                    "They come off the Hub and stop being assigned automatically. " +
                        "Designs they are already named on keep their name."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeKarigar(karigar.id); removing = null }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    removingActivity?.let { activity ->
        AlertDialog(
            onDismissRequest = { removingActivity = null },
            title = { Text("Remove $activity?") },
            text = { Text("Anyone assigned to it is unassigned. Nobody is removed from the Hub.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeActivity(activity); removingActivity = null }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { removingActivity = null }) { Text("Cancel") } },
        )
    }
    // #endregion
}

// #endregion

// #region 2. The board

@Composable
private fun SectionHeading(title: String, hint: String? = null, onAdd: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onAdd?.let {
            IconButton(onClick = it) { Icon(Icons.Default.Add, contentDescription = "Add $title") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KarigarPool(
    karigars: List<Karigar>,
    dragging: Karigar?,
    onDragStart: (Karigar, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRename: (Karigar) -> Unit,
    onRemove: (Karigar) -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionHeading("Karigars", "Tap a name to rename or remove.", onAdd)
        Spacer(Modifier.height(8.dp))
        if (karigars.isEmpty()) {
            Text(
                "Nobody on the list yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                karigars.forEach { karigar ->
                    DraggableKarigarChip(
                        karigar = karigar,
                        isBeingDragged = dragging?.id == karigar.id,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onRename = onRename,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityDropZone(
    activity: String,
    assigned: List<Karigar>,
    isHovered: Boolean,
    isDragging: Boolean,
    onBounds: (Rect) -> Unit,
    onUnassign: (Karigar) -> Unit,
    onRemoveActivity: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Only outlined while a drag is in progress: a dashed edge on a resting card reads as an
    // empty slot rather than as a target, and five of them would make the board look broken.
    val border = when {
        isHovered -> scheme.primary
        isDragging -> scheme.outlineVariant
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isHovered) scheme.primaryContainer else scheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DropZoneMinHeight)
            .onGloballyPositioned { onBounds(Rect(it.positionInRoot(), it.size.toSize())) }
            .border(2.dp, border, RoundedCornerShape(20.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    activity,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHovered) scheme.onPrimaryContainer else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemoveActivity, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $activity",
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (assigned.isEmpty()) {
                Text(
                    if (isDragging) "Drop here" else "Nobody assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    assigned.forEach { karigar ->
                        AssignedChip(karigar.name) { onUnassign(karigar) }
                    }
                }
            }
        }
    }
}

// #endregion

// #region 3. Chips and dragging

@Composable
private fun DraggableKarigarChip(
    karigar: Karigar,
    isBeingDragged: Boolean,
    onDragStart: (Karigar, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRename: (Karigar) -> Unit,
    onRemove: (Karigar) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(ChipRadius),
        // The chip stays in place and dims while its copy is dragged, so the pool does not
        // reflow mid-gesture and move every other name out from under the finger.
        color = if (isBeingDragged) scheme.surfaceContainerHighest else scheme.secondaryContainer,
        contentColor = if (isBeingDragged) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
        modifier = Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(karigar.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(karigar, origin + offset) },
                    onDrag = { change, delta -> change.consume(); onDrag(delta) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(ChipRadius))
                .clickable { menuOpen = true }
                .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(karigar.name, style = MaterialTheme.typography.labelLarge)
        }
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(karigar.name) },
            text = {
                Text(
                    if (karigar.activities.isEmpty()) "Not assigned to anything yet."
                    else "Assigned to: " + karigar.activities.joinToString(", ")
                )
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false; onRename(karigar) }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { menuOpen = false; onRemove(karigar) }) { Text("Remove") }
            },
        )
    }
}

@Composable
private fun AssignedChip(name: String, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(ChipRadius),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Unassign $name",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// #endregion

// #region 4. Dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// #endregion
