package com.sc.biap.ui.profile

/**
 * @file AccessManagementScreen.kt
 * @description Assign access levels to colleagues, within what the actor is allowed to do.
 *
 * The screen only ever OFFERS what the server would accept. Every restriction shown here -
 * an Admin unable to promote another Admin, nobody able to change their own level - is also
 * enforced in firestore.rules. This is the courtesy layer; that file is the security.
 */

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.InviteStore
import com.sc.biap.data.OrgMember
import com.sc.biap.security.AppLock
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.titleLargeStrong

private val RowSpinnerSize = 20.dp
private val LockedIconSize = 18.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessManagementViewModel = viewModel { AccessManagementViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showInviteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.error, state.lastChange) {
        val message = state.error ?: state.lastChange
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    // True only between opening the messaging app and coming back, so the app-lock bracket
    // below is released exactly once. Releasing on every resume would decrement a counter
    // this screen never incremented, and could cancel a bracket some other flow was relying
    // on - the counter exists precisely so overlapping flows do not clear each other.
    var awaitingSmsReturn by remember { mutableStateOf(false) }

    // Hand the finished invite to the messaging app. The app composes; the human sends.
    LaunchedEffect(state.smsToSend) {
        val sms = state.smsToSend ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:${sms.phone}".toUri()).apply {
            putExtra("sms_body", sms.body)
        }
        // Leaving for the SMS app looks exactly like leaving BIAP, and without this the
        // fingerprint prompt greets you on the way back.
        AppLock.beginTrustedFlow()
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (opened) {
            awaitingSmsReturn = true
        } else {
            AppLock.endTrustedFlow()
            // No messaging app at all. The invite is already stored and still valid, so say
            // what happened rather than letting it look like nothing did.
            snackbarHostState.showSnackbar(
                "Invite saved, but no messaging app opened. Send them the number yourself."
            )
        }
        viewModel.smsHandedOff()
    }

    // Returning from the messaging app closes the bracket opened above.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSmsReturn) {
                AppLock.endTrustedFlow()
                awaitingSmsReturn = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the screen mid-flow must not strand the lock permanently open.
            if (awaitingSmsReturn) AppLock.endTrustedFlow()
        }
    }

    if (showInviteDialog) {
        InviteDialog(
            levels = viewModel.invitableLevels(),
            isSending = state.isInviting,
            onDismiss = { showInviteDialog = false },
            onInvite = { phone, level ->
                showInviteDialog = false
                viewModel.invite(phone, level)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Team Access", style = MaterialTheme.typography.titleLargeStrong) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.myLevel.canManageAccess) {
                ExtendedFloatingActionButton(
                    onClick = { showInviteDialog = true },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Invite") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> LoadingIndicator(Modifier.align(Alignment.Center))

                // Reached without permission - possible if a level changed while the screen
                // was open. Says so plainly rather than showing an empty list.
                !state.myLevel.canManageAccess -> NotPermitted(Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    contentPadding = PaddingValues(AtelierSpacing.Base),
                    verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
                ) {
                    item {
                        Text(
                            "You are ${state.myLevel.label}. " + when (state.myLevel) {
                                AccessLevel.SUPER_ADMIN ->
                                    "You can set anyone's level, including Admins."
                                else ->
                                    "You can set levels below Admin. Only a Super Admin can " +
                                        "appoint or change an Admin."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = AtelierSpacing.Compact)
                        )
                    }

                    items(state.members, key = { it.uid }) { member ->
                        MemberRow(
                            member = member,
                            assignable = viewModel.assignableLevels(member),
                            isPending = state.pendingUid == member.uid,
                            onAssign = { viewModel.assign(member, it) }
                        )
                    }

                    if (state.invites.isNotEmpty()) {
                        item {
                            Text(
                                "Invited, not yet joined",
                                style = MaterialTheme.typography.labelLargeStrong,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AtelierSpacing.Base)
                            )
                        }
                        items(state.invites, key = { "invite:${it.phone}" }) { invite ->
                            InviteRow(invite = invite, onRevoke = { viewModel.revokeInvite(invite) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MemberRow(
    member: OrgMember,
    assignable: List<AccessLevel>,
    isPending: Boolean,
    onAssign: (AccessLevel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val locked = assignable.isEmpty()

    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    member.displayName + if (member.isSelf) " (you)" else "",
                    style = MaterialTheme.typography.bodyMediumStrong
                )
                if (member.contact.isNotBlank()) {
                    Text(
                        member.contact,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                isPending -> LoadingIndicator(Modifier.size(RowSpinnerSize))

                locked -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Micro)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        // Explains WHY it is locked; "you cannot edit this" without a reason
                        // reads as a bug rather than a rule.
                        contentDescription = if (member.isSelf) {
                            "Nobody can change their own access level"
                        } else {
                            "Only a Super Admin can change this level"
                        },
                        modifier = Modifier.size(LockedIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        member.level.label,
                        style = MaterialTheme.typography.labelLargeStrong,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> Box {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { expanded = true }) {
                        Text(member.level.label, style = MaterialTheme.typography.labelLargeStrong)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Change access level")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        assignable.forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            level.label,
                                            style = if (level == member.level) {
                                                MaterialTheme.typography.labelLargeStrong
                                            } else {
                                                MaterialTheme.typography.bodyMedium
                                            }
                                        )
                                        Text(
                                            level.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    onAssign(level)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One outstanding invite.
 *
 * Shown alongside the members so a manager can see the whole organisation - joined and
 * pending - in one place, rather than wondering whether an invite was ever sent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InviteRow(invite: InviteStore.Invite, onRevoke: () -> Unit) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
        ) {
            Column(Modifier.weight(1f)) {
                Text(invite.phone, style = MaterialTheme.typography.bodyMediumStrong)
                Text(
                    "Will join as ${invite.level.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRevoke) {
                Text("Withdraw")
            }
        }
    }
}

/**
 * Compose an invite: a number, and the level they arrive at.
 *
 * The level list is only what this actor could assign directly, so an invite cannot be used
 * to hand out something they had no authority to give.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InviteDialog(
    levels: List<AccessLevel>,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onInvite: (String, AccessLevel) -> Unit,
) {
    var phone by remember { mutableStateOf("+91") }
    var level by remember { mutableStateOf(levels.lastOrNull() ?: AccessLevel.SUPPORT_STAFF) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite someone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { expanded = true }
                    ) {
                        Text("Joins as ${level.label}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose level")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        levels.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label)
                                        Text(
                                            option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { level = option; expanded = false }
                            )
                        }
                    }
                }

                Text(
                    "They must sign in with this exact number to accept. " +
                        "Your messaging app opens with the invite ready to send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = !isSending,
                onClick = { onInvite(phone, level) }
            ) {
                Text("Create invite")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotPermitted(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(AtelierSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Only Admins can manage access", style = MaterialTheme.typography.bodyMediumStrong)
        Text(
            "Ask a Super Admin or Admin to change access levels.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
