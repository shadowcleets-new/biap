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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.data.Design
import com.sc.biap.data.Order
import com.sc.biap.ui.main.rupees
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @file OrdersList.kt
 * @description The Orders tab: what has gone out, newest first.
 *
 * [TABLE OF CONTENTS]
 * 1. LOCAL CONSTANTS & CONFIG
 * 2. THE LIST
 * 3. AN ORDER CARD
 * 4. CANCELLING
 * 5. THE EMPTY STATE
 *
 * DELIBERATELY NOT AN ORDER BOOK:
 *   No statuses, no customers, no invoices. An order here is a timestamped list of pieces
 *   that left, and the only thing that can happen to it afterwards is being cancelled -
 *   which puts the pieces back. Everything past that is a decision still to be made.
 *
 * WHY CANCELLING ASKS FIRST:
 *   It moves stock. Every other list in this app is safe to tap around in; this one has an
 *   action that changes the catalogue, so it stops and says what it is about to put back.
 */

// #region 1. Local constants & config

private val CardRadius = 28.dp
private val BadgeSize = 52.dp

// #endregion

// #region 2. The list

@Composable
fun OrdersList(
    orders: List<Order>,
    designNames: Map<String, String>,
    onCancel: (Order) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(orders, key = { it.id }) { order ->
            OrderCard(
                order = order,
                designNames = designNames,
                onCancel = { onCancel(order) },
            )
        }
    }
}

// #endregion

// #region 3. An order card

/**
 * One order, collapsed to its shape and expandable to its contents.
 *
 * Collapsed says how much and how much it was worth, because that is what a glance down the
 * list is for. The lines are one tap away rather than always on: an order of eight pieces
 * across four designs would otherwise be taller than the screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrderCard(
    order: Order,
    designNames: Map<String, String>,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(order.id) { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    val stamp = remember(order.createdAt) {
        SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(order.createdAt))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(scheme.surfaceContainerHigh)
            .clickable { expanded = !expanded }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(BadgeSize)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = order.pieces.toString(),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = scheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (order.pieces == 1) "1 piece" else "${order.pieces} pieces",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                )
                Text(
                    text = buildList {
                        add(
                            if (order.designCount == 1) "1 design"
                            else "${order.designCount} designs"
                        )
                        add(stamp)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rupees(order.total),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                )
                if (order.hasUnpricedLines) {
                    // Said rather than hidden. A total that quietly omits unpriced pieces
                    // reads as the real figure, and it is not.
                    Text(
                        "some unpriced",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide pieces" else "Show pieces",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            order.lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${line.quantity}×",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        modifier = Modifier.width(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            // A design deleted after the order was placed still has to name
                            // itself somehow, hence the fallback rather than a blank row.
                            text = designNames[line.designId] ?: "Design no longer in catalogue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "Size ${line.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (line.unitPrice > 0L) rupees(line.quantity * line.unitPrice) else "No price",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.unitPrice > 0L) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { confirming = true },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cancel order")
            }
        }
    }

    if (confirming) {
        CancelOrderDialog(
            order = order,
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                onCancel()
            },
        )
    }
}

// #endregion

// #region 4. Cancelling

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CancelOrderDialog(order: Order, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this order?") },
        text = {
            Text(
                "The ${order.pieces} ${if (order.pieces == 1) "piece" else "pieces"} on it go " +
                    "back into stock, and the order is removed from this list. Nothing else " +
                    "about the designs changes.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) { Text("Cancel order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Keep it") }
        },
    )
}

// #endregion

// #region 5. The empty state

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrdersEmptyState(
    hasDesigns: Boolean,
    onNewOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "0",
                style = MaterialTheme.typography.displayMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Nothing has gone out yet",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = scheme.onSurface,
            letterSpacing = (-0.8).sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (hasDesigns) {
                "Make an order to pick pieces off your catalogue. Whatever you pick comes " +
                    "straight out of the stock in Inventory."
            } else {
                "There is nothing to order yet. Add a design with its sizes and stock in the " +
                    "Inventory tab first."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )

        if (hasDesigns) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onNewOrder,
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(percent = 50)),
                modifier = Modifier.height(64.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Make first order", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// #endregion

/** Design id to name, for the lines. Built once per list rather than searched per line. */
fun designNameLookup(designs: List<Design>): Map<String, String> =
    designs.associate { it.id to it.name }
