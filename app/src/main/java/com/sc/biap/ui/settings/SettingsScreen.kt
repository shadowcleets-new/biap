package com.sc.biap.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.security.AppLock
import com.sc.biap.security.AppLockPreference

/**
 * @file SettingsScreen.kt
 * @description The app's own preferences, in the Expressive language.
 *
 * GROUPED TONAL CONTAINERS, NOT A LIST OF ROWS WITH DIVIDERS:
 *   Expressive builds lists out of grouped containers - the first item takes a large top
 *   radius, the last a large bottom one, and the middles stay tight. That shape run is what
 *   says "these belong together", so dividers are unnecessary and would only add noise.
 *
 * Every colour is a Material role. The palette comes from the wallpaper at runtime, so any
 * literal written here would be wrong on every phone but one.
 */

private val GroupRadiusLarge = 28.dp
private val GroupRadiusTight = 6.dp
private val RowMinHeight = 76.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val lockEnabled by AppLockPreference.enabled.collectAsState()
    // A device with no fingerprint and no PIN has nothing to authenticate against. The
    // switch is shown but disabled, with the reason stated - silently hiding it would look
    // like the feature does not exist.
    val canAuthenticate = remember { AppLock.canAuthenticate(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = scheme.onSurface,
            letterSpacing = (-1.5).sp,
        )

        Spacer(Modifier.height(28.dp))

        SectionLabel("Security")

        Spacer(Modifier.height(10.dp))

        SettingsSwitchRow(
            icon = Icons.Default.Fingerprint,
            title = "Require unlock",
            supporting = when {
                !canAuthenticate ->
                    "Set a screen lock on this phone to use it."
                lockEnabled ->
                    "Your fingerprint or screen lock is needed to open the catalogue."
                else ->
                    "Anyone holding this phone can open your costs and margins."
            },
            checked = lockEnabled && canAuthenticate,
            enabled = canAuthenticate,
            onCheckedChange = { AppLockPreference.set(context, it) },
            // Single item in its group, so both ends are fully rounded.
            shape = RoundedCornerShape(GroupRadiusLarge),
        )

        Spacer(Modifier.height(12.dp))

        // Says what the lock does and does not protect. A security control that overstates
        // itself is worse than none, because it buys confidence it has not earned.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupRadiusLarge))
                .background(scheme.surfaceContainerHighest)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Your catalogue is encrypted on this device either way. This lock " +
                    "decides whether someone already holding your unlocked phone can read it.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp,
    )
}

/**
 * One setting: an icon, a title, a plain-English consequence, and a switch.
 *
 * The whole row is the target, not just the switch. A 52dp switch is a small thing to hit
 * accurately, and there is nothing else the row could mean.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (checked) scheme.secondaryContainer else scheme.surfaceContainerHigh
    val onContainer = if (checked) scheme.onSecondaryContainer else scheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(shape)
            .background(if (enabled) container else scheme.surfaceContainer)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) onContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (enabled) onContainer else scheme.onSurfaceVariant,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = (if (enabled) onContainer else scheme.onSurfaceVariant)
                    .copy(alpha = 0.8f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            // Expressive switches carry an icon inside the thumb when on - the tick is the
            // state, not just the position, which is what makes it readable at a glance and
            // to anyone who cannot separate the two track colours.
            thumbContent = if (checked) {
                {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null,
        )
    }
}
