package com.sc.biap.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import com.sc.biap.data.stats
import com.sc.biap.ui.main.CatalogueSearchField
import com.sc.biap.ui.main.DesignThumbnail
import com.sc.biap.ui.main.rupees
import com.sc.biap.util.SizeUtils

/**
 * @file NewOrderScreen.kt
 * @description Picking pieces off the catalogue to make an order.
 *
 * [TABLE OF CONTENTS]
 * 1. LOCAL CONSTANTS & CONFIG
 * 2. THE SCREEN
 * 3. A DESIGN AND ITS SIZES
 * 4. THE STEPPER
 * 5. THE RUNNING TOTAL
 *
 * ONE SCREEN, NOT A WIZARD:
 *   An order can be one size of one design or a dozen sizes across several, and both are
 *   ordinary. A stepped flow would make the small case tedious to serve the large one; a
 *   single searchable list with a stepper per size serves both at the same cost.
 *
 * THE STEPPER STOPS AT THE STOCK:
 *   You cannot pick more of a size than exists. The cap is enforced again in the repository,
 *   inside the transaction, because the stock can move between this screen opening and the
 *   order being placed - but stopping the finger here is what keeps that from ever being a
 *   surprise.
 */

// #region 1. Local constants & config

private val CardRadius = 28.dp
private val StepperButtonSize = 36.dp

/**
 * The preview, portrait like the one in the catalogue and for the same reason: these are
 * full-length shots, and a square crop takes the head and the hem.
 *
 * Smaller than the catalogue's 104x138. This list is a picking list - you scroll it looking
 * for a garment you already have in mind - so it has to stay dense enough to scan. At this
 * size roughly five designs fit a screen instead of seven, which is the cost of the photo and
 * worth paying; at the catalogue's size it would have been three.
 */
private val ThumbWidth = 84.dp
private val ThumbHeight = 112.dp
private val ThumbRadius = 20.dp

// #endregion

// #region 2. The screen

@Composable
fun defaultNewOrderViewModel(): NewOrderViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { NewOrderViewModel(DefaultDataRepository.getInstance(context)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewOrderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewOrderViewModel = defaultNewOrderViewModel(),
) {
    val designs by viewModel.designs.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            OrderTotalBar(
                draft = draft,
                saving = saving,
                onPlace = { viewModel.place(onPlaced = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "New order",
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        "PICK THE PIECES GOING OUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                    )
                }
            }

            CatalogueSearchField(
                query = query,
                onQueryChange = { viewModel.updateQuery(it) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            if (designs.isEmpty()) {
                NothingToOrder(query = query, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(designs, key = { it.id }) { design ->
                        DesignPicker(
                            design = design,
                            picks = picks,
                            onSetQuantity = { size, qty ->
                                viewModel.setQuantity(design.id, size.size, qty, size.stock)
                            },
                        )
                    }
                }
            }
        }
    }
}

// #endregion

// #region 3. A design and its sizes

/**
 * A design, collapsed by default, expanding to a stepper per size.
 *
 * Opens itself once something has been picked from it, and stays open, so a design you are
 * part-way through never collapses out from under you as the list recomposes.
 *
 * THE PHOTO SITS IN THE HEADER, NOT BESIDE THE SIZES:
 *   Beside the sizes it would leave a growing column of dead space under itself - a design
 *   with five sizes is three times taller than the picture. In the header it is read once,
 *   for recognition, and the steppers keep the full width they need. Same arrangement as a
 *   catalogue row, which is the point: the two lists show the same designs and should not
 *   look like two different apps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DesignPicker(
    design: Design,
    picks: Map<PieceKey, Int>,
    onSetQuantity: (SizeInventory, Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val picked = picks.entries.filter { it.key.designId == design.id }.sumOf { it.value }
    var expanded by rememberSaveable(design.id) { mutableStateOf(false) }
    val open = expanded || picked > 0
    val stats = remember(design) { design.stats() }

    // Sold-out sizes are dropped rather than shown disabled: a row you can never use is
    // noise in a list you are scanning for the ones you can.
    val orderable = remember(design.sizeInventories) {
        design.sizeInventories
            .filter { it.stock > 0 }
            .sortedBy { SizeUtils.getSizeSortWeight(it.size) }
    }

    val onCard = if (picked > 0) scheme.onSecondaryContainer else scheme.onSurface
    val onCardMuted = if (picked > 0) {
        scheme.onSecondaryContainer.copy(alpha = 0.75f)
    } else {
        scheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(if (picked > 0) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable { expanded = !open }
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .clip(RoundedCornerShape(ThumbRadius))
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { DesignThumbnail(design, ThumbWidth) }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = design.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = onCard,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = design.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = onCardMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Icon(
                        if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (open) "Hide sizes" else "Show sizes",
                        tint = onCardMuted,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    // Same rule as everywhere else: an unpriced size never sets the floor,
                    // and a design with no priced size at all says so rather than reading ₹0.
                    text = when {
                        stats.minPrice == null -> "Not priced yet"
                        stats.minPrice == stats.maxPrice -> rupees(stats.minPrice!!)
                        else -> "${rupees(stats.minPrice!!)} – ${rupees(stats.maxPrice!!)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard,
                )
                Text(
                    text = if (picked > 0) {
                        "$picked picked · ${orderable.size} in stock"
                    } else {
                        "${orderable.size} ${if (orderable.size == 1) "size" else "sizes"} in stock"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onCardMuted,
                )
            }
        }

        if (open) {
            Spacer(Modifier.height(12.dp))
            orderable.forEach { size ->
                SizePickerRow(
                    size = size,
                    quantity = picks[PieceKey(design.id, size.size)] ?: 0,
                    onQuantity = { onSetQuantity(size, it) },
                    onContainer = picked > 0,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizePickerRow(
    size: SizeInventory,
    quantity: Int,
    onQuantity: (Int) -> Unit,
    onContainer: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val primaryText = if (onContainer) scheme.onSecondaryContainer else scheme.onSurface
    val secondaryText = if (onContainer) {
        scheme.onSecondaryContainer.copy(alpha = 0.75f)
    } else {
        scheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Size, stock and price on ONE line, sitting together on the left.
        //
        // These were two stacked lines with the label in its own fixed column, which left a
        // wide empty channel between the price and the stepper and made every row 64dp tall.
        // Read together they are one fact - "L, three left, ₹995" - and one line of it fits
        // four sizes in the space three used to take.
        Text(
            text = size.size,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = primaryText,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            // "No price" rather than a confident ₹0. A size added before its price was
            // known is a real and ordinary state, and rendering it as free is a lie.
            text = "${size.stock} left · " +
                (if (size.price > 0L) rupees(size.price) else "No price"),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Stepper(
            quantity = quantity,
            max = size.stock,
            onQuantity = onQuantity,
            contentColor = primaryText,
            containerColor = if (onContainer) {
                scheme.onSecondaryContainer.copy(alpha = 0.12f)
            } else {
                scheme.surfaceContainerHighest
            },
        )
    }
}

// #endregion

// #region 4. The stepper

/** Minus, a count, plus. Both ends disable at their limit rather than silently doing nothing. */
@Composable
private fun Stepper(
    quantity: Int,
    max: Int,
    onQuantity: (Int) -> Unit,
    contentColor: Color,
    containerColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton(
            icon = Icons.Default.Remove,
            description = "One fewer",
            enabled = quantity > 0,
            container = containerColor,
            content = contentColor,
            onClick = { onQuantity(quantity - 1) },
        )
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepperButton(
            icon = Icons.Default.Add,
            description = "One more",
            enabled = quantity < max,
            container = containerColor,
            content = contentColor,
            onClick = { onQuantity(quantity + 1) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(StepperButtonSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(container.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = content.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}

// #endregion

// #region 5. The running total

/**
 * The bottom bar: what is on the draft, and the one button that commits it.
 *
 * Always present rather than appearing once something is picked, so the button never arrives
 * under a finger that was reaching for a stepper.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderTotalBar(draft: OrderDraft, saving: Boolean, onPlace: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (draft.isEmpty) {
                    "Nothing picked"
                } else {
                    "${draft.pieces} ${if (draft.pieces == 1) "piece" else "pieces"} · " +
                        "${draft.designCount} ${if (draft.designCount == 1) "design" else "designs"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = rupees(draft.total),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = scheme.onSurface,
            )
            if (draft.hasUnpriced) {
                Text(
                    "Some picked sizes have no price set",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onPlace,
            enabled = !draft.isEmpty && !saving,
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
            modifier = Modifier.height(56.dp),
        ) {
            Text(
                if (saving) "Placing…" else "Place order",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NothingToOrder(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (query.isBlank()) "Nothing in stock" else "No matches",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (query.isBlank()) {
                "Every design is either sold out or archived, so there is nothing to send out."
            } else {
                "Nothing in stock matches \"$query\"."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )
    }
}

// #endregion
