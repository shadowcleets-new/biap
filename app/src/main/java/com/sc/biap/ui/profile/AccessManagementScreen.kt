package com.sc.biap.ui.profile

/**
 * @file AccessManagementScreen.kt
 * @description Assign access levels to colleagues, within what the actor is allowed to do.
 *
 * The screen only ever OFFERS what the server would accept. Every restriction shown here -
 * an Admin unable to promote another Admin, nobody able to change their own level - is also
 * enforced in firestore.rules. This is the courtesy layer; that file is the security.
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.OrgMember
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.titleLargeStrong

private val RowSpinnerSize = 20.dp
private val LockedIconSize = 18.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessManagementViewModel = viewModel { AccessManagementViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.lastChange) {
        val message = state.error ?: state.lastChange
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

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
                }
            }
        }
    }
}

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
                isPending -> CircularProgressIndicator(Modifier.size(RowSpinnerSize), strokeWidth = 2.dp)

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
                    TextButton(onClick = { expanded = true }) {
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
