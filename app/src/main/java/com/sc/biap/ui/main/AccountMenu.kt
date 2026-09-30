package com.sc.biap.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @file AccountMenu.kt
 * @description The account menu, rebuilt in the Material 3 Expressive menu language.
 *
 * WHAT MAKES IT EXPRESSIVE RATHER THAN JUST A LIST:
 *   Material 3 Expressive builds menus out of GROUPED CONTAINERS, not flat rows separated by
 *   dividers. Each item is its own filled surface, and the shapes carry the grouping: the
 *   first item in a group takes a large radius on top, the last takes it on the bottom, and
 *   the ends where items meet stay tight. That run of shapes is what says "these belong
 *   together", which is why there are no dividers here - they would be saying it twice.
 *
 * THE SHAPES COME FROM MATERIAL, NOT FROM CONSTANTS HERE:
 *   MenuDefaults.itemShape(index, count) is a pure position-to-shape calculation, so this
 *   file never hardcodes a radius and inherits any change Google makes to the menu shape
 *   scale. The same goes for the gap between groups and the item container colour.
 *
 *   Only the CONTAINER composable is ours. DropdownMenuGroup assumes everything inside it is
 *   a menu item sharing one colour, which the account header (not an item, no ripple) and
 *   Sign out (its own colour, its own group) both break. Those are layout decisions; the
 *   geometry is still Material's.
 *
 * COLOUR CARRIES MEANING, NOT DECORATION:
 *   Ordinary destinations sit on the standard group colour. Sign out sits on errorContainer,
 *   alone, separated by a real gap - it is the one item you cannot undo by tapping again.
 */

private val MenuWidth = 268.dp
private val ItemHeight = 56.dp
private val ItemGap = 2.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    accountLabel: String,
    isSyncing: Boolean,
    canManageAccess: Boolean,
    onSync: () -> Unit,
    onProfile: () -> Unit,
    onBusinessHub: () -> Unit,
    onTeamAccess: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = scheme.surfaceContainer,
        modifier = Modifier.width(MenuWidth),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            // Who you are. Not an item - nothing happens if you press it - so it carries no
            // container and no ripple, only the label.
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "SIGNED IN AS",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = accountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // --- destinations, one group ------------------------------------
            //
            // Built as a list so the index and count fed to MenuDefaults.itemShape() are
            // always correct. Team Access appears conditionally, and hardcoding positions
            // around it is exactly how a group ends up with two rounded ends in the middle.
            val destinations = buildList {
                add(
                    MenuEntry(
                        icon = Icons.Default.Sync,
                        label = if (isSyncing) "Syncing…" else "Sync with Drive",
                        enabled = !isSyncing,
                        // The spinner replaces the icon rather than sitting beside it, so
                        // the row never changes width while a sync runs.
                        busy = isSyncing,
                        onClick = onSync,
                    )
                )
                add(MenuEntry(Icons.Default.Person, "Profile", onClick = onProfile))
                add(MenuEntry(Icons.Default.Handyman, "Business Hub", onClick = onBusinessHub))
                if (canManageAccess) {
                    add(MenuEntry(Icons.Default.Groups, "Team Access", onClick = onTeamAccess))
                }
                add(MenuEntry(Icons.Default.Settings, "Settings", onClick = onSettings))
            }

            destinations.forEachIndexed { index, entry ->
                MenuAction(
                    entry = entry,
                    shape = MenuDefaults.itemShape(index, destinations.size).shape,
                    container = MenuDefaults.groupStandardContainerColor,
                )
            }

            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

            // --- the one you cannot undo, alone ------------------------------
            MenuAction(
                entry = MenuEntry(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Sign out",
                    onClick = onSignOut,
                ),
                // A group of one: Material gives it both ends rounded.
                shape = MenuDefaults.itemShape(0, 1).shape,
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
            )
        }
    }
}

/** One row's worth of content, so position and shape stay the caller's business. */
private data class MenuEntry(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuAction(
    entry: MenuEntry,
    shape: Shape,
    container: Color,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (entry.enabled) content else scheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItemHeight)
            .clip(shape)
            .background(container)
            .clickable(enabled = entry.enabled, onClick = entry.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (entry.busy) {
            LoadingIndicator(modifier = Modifier.size(22.dp), color = fg)
        } else {
            Icon(entry.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
