package com.sc.biap.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sc.biap.data.DefaultDataRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import com.sc.biap.theme.ThemeMode
import com.sc.biap.theme.ThemePreference
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
    val repository = remember { DefaultDataRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var showChange by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var dialogBusy by remember { mutableStateOf(false) }

    if (showChange) {
        ChangePassphraseDialog(
            error = dialogError,
            busy = dialogBusy,
            onDismiss = { showChange = false; dialogError = null },
            onConfirm = { current, next ->
                dialogBusy = true
                dialogError = null
                scope.launch {
                    val failure = repository.changeSyncPassphrase(current, next)
                    dialogBusy = false
                    if (failure == null) showChange = false else dialogError = failure
                }
            },
        )
    }

    if (showReset) {
        ResetPassphraseDialog(
            designCount = repository.localDesignCount(),
            error = dialogError,
            busy = dialogBusy,
            onDismiss = { showReset = false; dialogError = null },
            onConfirm = { next ->
                dialogBusy = true
                dialogError = null
                scope.launch {
                    val failure = repository.resetSyncPassphrase(next)
                    dialogBusy = false
                    if (failure == null) showReset = false else dialogError = failure
                }
            },
        )
    }

    val lockEnabled by AppLockPreference.enabled.collectAsState()
    // A device with no fingerprint and no PIN has nothing to authenticate against. The
    // switch is shown but disabled, with the reason stated - silently hiding it would look
    // like the feature does not exist.
    val canAuthenticate = remember { AppLock.canAuthenticate(context) }
    val themeMode by ThemePreference.mode.collectAsState()

    // Scrollable, because the content is already taller than a phone screen and grows every
    // time a setting is added. Without this the last option is simply unreachable - which is
    // exactly what happened to AMOLED Black.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
            .verticalScroll(rememberScrollState())
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
        InfoCard(
            icon = Icons.Default.Lock,
            text = "Your catalogue is encrypted on this device either way. This lock " +
                "decides whether someone already holding your unlocked phone can read it.",
        )

        Spacer(Modifier.height(12.dp))

        PassphraseRow(
            onChange = { dialogError = null; showChange = true },
            onReset = { dialogError = null; showReset = true },
        )

        Spacer(Modifier.height(28.dp))

        SectionLabel("Appearance")

        Spacer(Modifier.height(10.dp))

        ThemeSelector(
            selected = themeMode,
            onSelect = { ThemePreference.set(context, it) },
        )

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * The four theme modes as a vertical group.
 *
 * A group of radio-style rows rather than a segmented control: each option carries a line
 * of explanation, and "switches to AMOLED black while battery saver is on" does not fit in
 * a segment. Shapes run leading-to-trailing so the group reads as one block.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val modes = ThemeMode.entries

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        modes.forEachIndexed { index, mode ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(
                topStart = if (index == 0) GroupRadiusLarge else GroupRadiusTight,
                topEnd = if (index == 0) GroupRadiusLarge else GroupRadiusTight,
                bottomStart = if (index == modes.lastIndex) GroupRadiusLarge else GroupRadiusTight,
                bottomEnd = if (index == modes.lastIndex) GroupRadiusLarge else GroupRadiusTight,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
                    )
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                            .copy(alpha = 0.85f),
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Passphrase management: change it, or start over having forgotten it.
 *
 * Both actions are offered together because the difference between them matters and is
 * easy to get wrong. Change keeps the existing backup readable; reset replaces it. Putting
 * them side by side, with reset in a quieter treatment, is what stops the destructive one
 * being tapped by reflex.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PassphraseRow(onChange: () -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = GroupRadiusLarge, topEnd = GroupRadiusLarge,
                        bottomStart = GroupRadiusTight, bottomEnd = GroupRadiusTight,
                    )
                )
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onChange)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = scheme.onSurface,
                modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Change backup passphrase",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                )
                Text(
                    "Keeps your existing backup readable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = GroupRadiusTight, topEnd = GroupRadiusTight,
                        bottomStart = GroupRadiusLarge, bottomEnd = GroupRadiusLarge,
                    )
                )
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onReset)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.LockReset, contentDescription = null,
                tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Forgot passphrase",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "Replaces the backup with a fresh copy from this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadiusLarge))
            .background(scheme.surfaceContainerHighest)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
