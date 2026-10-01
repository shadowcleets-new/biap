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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.unit.IntSize
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
/** How deep the scroll-on-drag zone reaches in from the top and bottom of the board. */
private val EdgeScrollZone = 96.dp
/** The most the board moves in one frame at the very edge - about 540dp a second at 60Hz. */
private val EdgeScrollMaxStep = 9.dp
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

    // Where the content Box sits in the window, and how big the floating chip turns out to
    // be. Both are needed to put the chip under the finger; see the offset below.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var addKarigarOpen by remember { mutableStateOf(false) }
    var addActivityOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Karigar?>(null) }
    var removing by remember { mutableStateOf<Karigar?>(null) }
    var removingActivity by remember { mutableStateOf<String?>(null) }
    var merging by remember { mutableStateOf<Karigar?>(null) }

    fun zoneAt(point: Offset): String? =
        dropZones.entries.firstOrNull { it.value.contains(point) }?.key

    // THE BOARD SCROLLS ITSELF WHILE YOU DRAG NEAR AN EDGE.
    //
    // Without this, an activity below the fold is simply unreachable: the finger is holding
    // the chip, so it cannot also flick the list, and letting go to scroll drops the chip
    // wherever it happens to be. Every drag-and-drop board has to solve this and the answer
    // is always the same - the edges of the viewport become scroll zones.
    //
    // Speed ramps with how far into the zone the finger is, so easing towards the edge
    // creeps and pushing right to it moves briskly. A fixed speed either crawls when you
    // want to cross the whole list or overshoots when you want the next card down.
    //
    // The drop zones need no special handling: they re-record their bounds through
    // onGloballyPositioned as the list moves, so the hit test stays correct mid-scroll.
    LaunchedEffect(dragging != null) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { EdgeScrollZone.toPx() }
        val maxStep = with(density) { EdgeScrollMaxStep.toPx() }
        while (true) {
            withFrameNanos { }
            val height = containerSize.height
            if (height <= 0) continue
            val y = dragPosition.y - containerOrigin.y
            val push = when {
                y < edge -> -(edge - y) / edge
                y > height - edge -> (y - (height - edge)) / edge
                else -> 0f
            }
            if (push != 0f) {
                listState.scrollBy(push.coerceIn(-1f, 1f) * maxStep)
                // The board moved under a finger that did not, so whatever is beneath it
                // has changed. Without this the highlight stays on the card the finger
                // left behind, and it points at the wrong drop right up until you let go.
                hovered = zoneAt(dragPosition)
            }
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned {
                    containerOrigin = it.positionInRoot()
                    containerSize = it.size
                },
        ) {
            LazyColumn(
                state = listState,
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
                        onMerge = { merging = it },
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

            // The chip that follows the finger. Drawn last, so it floats over the board.
            //
            // TWO CORRECTIONS, both of which it got wrong first time and which together put
            // it hundreds of pixels away from the finger:
            //
            //  - dragPosition is in WINDOW coordinates, because that is the space the drop
            //    zones record their bounds in and the two have to be comparable. This Surface
            //    is positioned relative to the content Box, which starts below the status bar
            //    and the top app bar. Subtracting the Box's own origin converts one to the
            //    other; without it the chip trails the finger by the height of everything
            //    above it.
            //  - the chip is centred on its MEASURED size rather than a guessed half-width.
            //    The guess was right for one name length and wrong for every other, so the
            //    error changed depending on whose name you picked up.
            dragging?.let { karigar ->
                Surface(
                    shape = RoundedCornerShape(ChipRadius),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .onGloballyPositioned { previewSize = it.size }
                        .offset {
                            IntOffset(
                                (dragPosition.x - containerOrigin.x - previewSize.width / 2f).roundToInt(),
                                (dragPosition.y - containerOrigin.y - previewSize.height / 2f).roundToInt(),
                            )
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 15.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        KarigarIcon(modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(karigar.name, style = MaterialTheme.typography.labelLarge)
                    }
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

    merging?.let { from ->
        val others = state.karigars.filter { it.id != from.id }
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text("Merge ${from.name} into") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Pick the name to keep. Every design saying ${from.name} will say that " +
                            "name instead, and ${from.name} comes off the Hub. Nothing is deleted " +
                            "from a design.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    others.forEach { into ->
                        Text(
                            into.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.mergeKarigar(from.id, into.id)
                                    merging = null
                                }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = null }) { Text("Cancel") } },
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
    onMerge: (Karigar) -> Unit,
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
                        isAssigned = karigar.activities.isNotEmpty(),
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onRename = onRename,
                        onRemove = onRemove,
                        onMerge = onMerge,
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
                ActivityIcon(
                    activity = activity,
                    modifier = Modifier.size(22.dp),
                    tint = if (isHovered) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
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
    isAssigned: Boolean,
    onDragStart: (Karigar, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRename: (Karigar) -> Unit,
    onRemove: (Karigar) -> Unit,
    onMerge: (Karigar) -> Unit,
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
            // A person, where the drag handle used to be. The hint above the activities
            // already says these can be dragged, and a name reads better with a face on it
            // than with a grab dot.
            KarigarIcon(modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(karigar.name, style = MaterialTheme.typography.labelLarge)
            // A tick once this person has work, so the pool answers "who have I not placed
            // yet" without reading every activity card below. Deliberately an addition
            // rather than a dimming: they are still draggable onto a second activity, and
            // greying them out would say the opposite.
            if (isAssigned && !isBeingDragged) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Already assigned",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
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
                Row {
                    TextButton(onClick = { menuOpen = false; onMerge(karigar) }) { Text("Merge") }
                    TextButton(onClick = { menuOpen = false; onRename(karigar) }) { Text("Rename") }
                }
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
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            KarigarIcon(modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
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
