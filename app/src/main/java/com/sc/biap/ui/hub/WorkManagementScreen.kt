package com.sc.biap.ui.hub

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.JobWork
import com.sc.biap.data.WorkKind
import com.sc.biap.data.WorkLot
import com.sc.biap.data.label
import com.sc.biap.data.overdueDays
import com.sc.biap.ui.main.DesignThumbnail
// #endregion

/**
 * @file WorkManagementScreen.kt
 * @description Where the goods are - samples, production and alterations in one list.
 *
 * WRITTEN FOR THE WHOLE WORKSHOP, NOT THE OWNER:
 *   Every card answers four things and nothing else - who has it, what, how long, and one
 *   button for what happens next. No card offers a second-best action; anything rarer lives
 *   behind opening the card. A person who has never used the app should be able to read a
 *   row and act on it without being taught.
 *
 *   Colour means one thing here: red is "chase this". Nothing else is coloured, so red never
 *   has to compete for attention with decoration.
 */
@Composable
fun defaultWorkManagementViewModel(): WorkManagementViewModel {
    val context = LocalContext.current
    return viewModel { WorkManagementViewModel(DefaultDataRepository.getInstance(context)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkManagementViewModel = defaultWorkManagementViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lots by viewModel.visibleLots.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val now = remember { System.currentTimeMillis() }

    var adding by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf<WorkLot?>(null) }
    var finishing by remember { mutableStateOf<WorkLot?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text("Work management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add work")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                WorkSummary(piecesOut = state.piecesOut, overdue = state.overdue)
            }

            item(key = "filters") {
                KindFilters(
                    selected = filter,
                    counts = state.countByKind,
                    onSelect = viewModel::setFilter,
                )
            }

            // Samples that have arrived and are waiting on a yes or a no. Kept above the
            // outstanding work because it is the only thing here that is waiting on the
            // OWNER rather than on a karigar.
            if (state.awaitingDecision.isNotEmpty() && filter.let { it == null || it == WorkKind.SAMPLE }) {
                item(key = "decide-heading") {
                    HubSectionHeading("Waiting on you", "Samples that have arrived.")
                }
                items(state.awaitingDecision.size, key = { "d-" + state.awaitingDecision[it].id }) { i ->
                    val sample = state.awaitingDecision[i]
                    SampleDecisionCard(
                        sample = sample,
                        onApprove = { viewModel.decideSample(sample.id, true) },
                        onReject = { viewModel.decideSample(sample.id, false) },
                    )
                }
            }

            if (lots.isEmpty()) {
                item(key = "empty") { NothingOutYet(filter) }
            } else {
                items(lots.size, key = { lots[it].lotId }) { i ->
                    val lot = lots[i]
                    WorkLotCard(
                        lot = lot,
                        design = state.designsById[lot.designId],
                        now = now,
                        onReceive = { receiving = lot },
                        onFinish = { finishing = lot },
                    )
                }
            }
        }
    }

    if (adding) {
        AddWorkSheet(
            state = state,
            onDismiss = { adding = false },
            onSample = { name, karigar, activity, note ->
                viewModel.startSample(name, karigar, activity, note, null)
                adding = false
            },
            onProduction = { designId, karigar, activity, quantities, batch, note ->
                viewModel.startProduction(designId, karigar, activity, quantities, batch, note)
                adding = false
            },
            onAlteration = { designId, size, karigar, qty, note ->
                viewModel.startAlteration(designId, size, karigar, qty, note, null)
                adding = false
            },
        )
    }

    receiving?.let { lot ->
        ReceiveLotDialog(
            lot = lot,
            onConfirmWhole = {
                viewModel.receiveWholeLot(lot)
                receiving = null
            },
            onConfirmRow = { rowId, qty ->
                viewModel.receive(rowId, qty)
                receiving = null
            },
            onDismiss = { receiving = null },
        )
    }

    finishing?.let { lot ->
        FinishLotDialog(
            lot = lot,
            onConfirm = {
                viewModel.close(lot)
                finishing = null
            },
            onDismiss = { finishing = null },
        )
    }
}

// #region Summary and filters

/** The two numbers the screen exists to show, said in words rather than as a chart. */
@Composable
private fun WorkSummary(piecesOut: Int, overdue: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        Text(
            when (piecesOut) {
                0 -> "Nothing out"
                1 -> "1 piece out"
                else -> "$piecesOut pieces out"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        if (overdue > 0) {
            Text(
                if (overdue == 1) "1 needs chasing" else "$overdue need chasing",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
            )
        }
    }
}

/**
 * Narrowing, not navigating.
 *
 * Chips rather than tabs so that the default view is EVERYTHING. The screen's whole job is
 * answering "what is out"; a tabbed version answers a third of it three times.
 */
@Composable
private fun KindFilters(
    selected: WorkKind?,
    counts: Map<WorkKind, Int>,
    onSelect: (WorkKind?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        WorkKind.entries.forEach { kind ->
            val n = counts[kind] ?: 0
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(if (selected == kind) null else kind) },
                label = { Text(if (n > 0) "${kind.label} $n" else kind.label) },
            )
        }
    }
}
// #endregion

// #region The card

/**
 * One lot, as a row anybody can act on.
 *
 * Days out is shown on every card, not only the late ones, because the question is not "is
 * this late" but "how long has he had them" - and because a number that only appears when
 * something is wrong teaches nobody what normal looks like.
 */
@Composable
private fun WorkLotCard(
    lot: WorkLot,
    design: com.sc.biap.data.Design?,
    now: Long,
    onReceive: () -> Unit,
    onFinish: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val late = lot.isOverdue(now)
    val days = lot.daysOut(now)
    val ink = if (late) scheme.onErrorContainer else scheme.onSurface
    val quiet = if (late) scheme.onErrorContainer else scheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (late) scheme.errorContainer else scheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lot.kind.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = quiet,
                modifier = Modifier.weight(1f),
            )
            Text(
                buildString {
                    append(lot.karigar)
                    append(" · ")
                    append(
                        when (days) {
                            0 -> "today"
                            1 -> "1 day"
                            else -> "$days days"
                        }
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = ink,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Only a real design has a photo for now; a sample's own picture arrives with
            // the media pass.
            design?.let {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surface),
                    contentAlignment = Alignment.Center,
                ) { DesignThumbnail(it, 56.dp) }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lot.rows.first().titleOr(design?.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                )
                val detail = buildString {
                    if (lot.batchNumber > 0) append("Batch ${lot.batchNumber} · ")
                    append("${lot.outstanding} of ${lot.sent}")
                    if (lot.received > 0) append(" still out")
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = quiet)
                if (lot.sizeBreakdown.isNotBlank()) {
                    Text(
                        lot.sizeBreakdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = quiet,
                    )
                }
            }
        }

        if (lot.note.isNotBlank()) {
            Text(lot.note, style = MaterialTheme.typography.bodySmall, color = quiet)
        }

        if (late) {
            Text(
                "With ${lot.karigar} for $days days - more than ${lot.kind.overdueDays}. Worth a call.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // One primary action. Anything rarer is behind Finish, which is deliberately
            // the quieter of the two.
            AssistChip(
                onClick = onReceive,
                label = { Text(if (lot.kind == WorkKind.SAMPLE) "Sample arrived" else "Received") },
            )
            TextButton(onClick = onFinish) {
                Text("Finish", color = quiet)
            }
        }
    }
}

/** A sample that has come back and is waiting on a yes or a no. */
@Composable
private fun SampleDecisionCard(
    sample: JobWork,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.secondaryContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sample.designName.ifBlank { "Untitled sample" },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    "From ${sample.karigar}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
        Text(
            "Approving adds it to your catalogue so you can show it and order a run.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSecondaryContainer,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onApprove, label = { Text("Approve") })
            TextButton(onClick = onReject) {
                Text("Not this one", color = scheme.onSecondaryContainer)
            }
        }
    }
}
// #endregion

// #region Empty

/**
 * The empty state, which exists to teach rather than to apologise.
 *
 * It names the exact path to the thing, because a reader who does not already know how work
 * gets here cannot find out by exploring an empty screen.
 */
@Composable
internal fun NothingOutYet(filter: WorkKind? = null) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (filter == null) "Nothing is out at the moment."
            else "No ${filter.label.lowercase()} work out at the moment.",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            "Anything with a karigar shows here - a sample being made, a run in production, " +
                "or pieces gone for alteration - with who has it and how long it has been gone.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            "Use the + button to add work, or open a design and tap a size to make more of it.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
// #endregion
