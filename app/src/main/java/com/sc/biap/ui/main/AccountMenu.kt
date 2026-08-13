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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 *   The library exposes this as MenuDefaults.itemShape(index, count) with matching
 *   leading/middle/trailing shapes. It is built by hand here for one reason: the account
 *   header is not a menu item and must not be shaped or highlighted like one, and the
 *   destructive Sign out sits in its own group with its own colour. Both would fight the
 *   stock grouping helper.
 *
 * COLOUR CARRIES MEANING, NOT DECORATION:
 *   Ordinary destinations sit on surfaceContainerHigh. Sign out sits on errorContainer,
 *   alone, separated by a real gap - it is the one item you cannot undo by tapping again.
 */

private val MenuWidth = 268.dp
private val ItemHeight = 56.dp
private val GroupRadius = 22.dp
private val JoinRadius = 6.dp
private val ItemGap = 2.dp

/** Where an item sits in its group, which is the only thing that decides its shape. */
private enum class Slot { SINGLE, FIRST, MIDDLE, LAST }

private fun Slot.shape(): RoundedCornerShape = when (this) {
    Slot.SINGLE -> RoundedCornerShape(GroupRadius)
    Slot.FIRST -> RoundedCornerShape(
        topStart = GroupRadius, topEnd = GroupRadius,
        bottomStart = JoinRadius, bottomEnd = JoinRadius,
    )
    Slot.MIDDLE -> RoundedCornerShape(JoinRadius)
    Slot.LAST -> RoundedCornerShape(
        topStart = JoinRadius, topEnd = JoinRadius,
        bottomStart = GroupRadius, bottomEnd = GroupRadius,
    )
}

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
            MenuAction(
                icon = Icons.Default.Sync,
                label = if (isSyncing) "Syncing…" else "Sync with Drive",
                slot = Slot.FIRST,
                enabled = !isSyncing,
                onClick = onSync,
                // The spinner replaces the icon rather than sitting beside it, so the row
                // never changes width while a sync runs.
                busy = isSyncing,
            )
            MenuAction(
                icon = Icons.Default.Person,
                label = "Profile",
                slot = Slot.MIDDLE,
                onClick = onProfile,
            )
            if (canManageAccess) {
                MenuAction(
                    icon = Icons.Default.Groups,
                    label = "Team Access",
                    slot = Slot.MIDDLE,
                    onClick = onTeamAccess,
                )
            }
            MenuAction(
                icon = Icons.Default.Settings,
                label = "Settings",
                slot = Slot.LAST,
                onClick = onSettings,
            )

            Spacer(Modifier.height(6.dp))

            // --- the one you cannot undo, alone ------------------------------
            MenuAction(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Sign out",
                slot = Slot.SINGLE,
                container = scheme.errorContainer,
                content = scheme.onErrorContainer,
                onClick = onSignOut,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    slot: Slot,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (enabled) content else scheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItemHeight)
            .clip(slot.shape())
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(22.dp), color = fg)
        } else {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
