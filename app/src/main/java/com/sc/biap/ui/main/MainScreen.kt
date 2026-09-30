package com.sc.biap.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.AccessLevelStore
import com.sc.biap.data.SyncStatus
import com.sc.biap.security.AppLock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.sc.biap.AddDesign
import com.sc.biap.DesignDetail
import com.sc.biap.AccessManagement
import com.sc.biap.NewOrder
import com.sc.biap.Profile
import com.sc.biap.Settings
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodySmallStrong
import com.sc.biap.theme.titleMediumStrong
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.sc.biap.ui.ProfileAvatar
import com.sc.biap.ui.orders.OrdersEmptyState
import com.sc.biap.ui.orders.OrdersList
import com.sc.biap.ui.rememberPerformanceProfile
import com.sc.biap.ui.PerformanceProfile

/**
 * The bottom navigation bar. Two destinations, filled icon when selected.
 *
 * Filled-when-selected, outlined otherwise, is Material's own convention for this component
 * and does the work a colour change alone would not: the selection is legible at a glance and
 * without relying on colour, which matters on the AMOLED theme where the indicator has very
 * little background left to contrast against.
 */
@Composable
private fun MainNavigationBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.INVENTORY,
            onClick = { onSelect(MainTab.INVENTORY) },
            icon = {
                Icon(
                    if (selected == MainTab.INVENTORY) Icons.Filled.Inventory2
                    else Icons.Outlined.Inventory2,
                    contentDescription = null,
                )
            },
            label = { Text("Inventory") },
        )
        NavigationBarItem(
            selected = selected == MainTab.ORDERS,
            onClick = { onSelect(MainTab.ORDERS) },
            icon = {
                Icon(
                    if (selected == MainTab.ORDERS) Icons.Filled.ReceiptLong
                    else Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                )
            },
            label = { Text("Orders") },
        )
    }
}

@Composable
fun defaultMainScreenViewModel(): MainScreenViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { MainScreenViewModel(DefaultDataRepository.getInstance(context)) }
}

// Component metrics: intrinsic sizes, deliberately off the AtelierSpacing scale, which
// governs layout rhythm rather than component size.
private val SyncSpinnerSmall = 20.dp
private val SyncSpinnerLarge = 28.dp
private val SyncSpinnerStroke = 2.dp

/**
 * The two halves of the same ledger: what came in, and what went out.
 *
 * A navigation bar rather than tabs at the top, per the Material guidance: these are two
 * top-level destinations, not two views of one thing, and the bar keeps both reachable with
 * a thumb on a phone held one-handed. Two destinations is at the low end of the three-to-five
 * the guidance suggests - a third would need to be a real destination rather than one
 * invented to fill the bar.
 */
private enum class MainTab(val title: String) {
    INVENTORY("Portfolio"),
    ORDERS("Orders"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = defaultMainScreenViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val authManager = viewModel.authManager
    // Drive grant (may be null while the user is perfectly well signed in).
    val currentUser by authManager.currentUser.collectAsStateWithLifecycle()
    // App identity, shown when there is no Google account to describe the user.
    val appIdentityLabel = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?.let { it.phoneNumber ?: it.email ?: it.displayName }
    }
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    // Drives whether the access-management entry is offered at all. Hiding it is a
    // courtesy, not the control: firestore.rules refuses the writes regardless.
    var myAccessLevel by remember { mutableStateOf(AccessLevel.DEFAULT) }
    LaunchedEffect(Unit) { myAccessLevel = AccessLevelStore.current().level }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Sync stopped for want of the passphrase that unwraps the sync key. Handled with a
    // dialog rather than a snackbar because it is a question, not a notification.
    var passphrasePrompt by remember { mutableStateOf<SyncStatus.PassphraseRequired?>(null) }

    LaunchedEffect(syncStatus) {
        when (val status = syncStatus) {
            is SyncStatus.PassphraseRequired -> {
                passphrasePrompt = status
                viewModel.resetSyncStatus()
            }
            is SyncStatus.Success -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.resetSyncStatus()
            }
            is SyncStatus.Error -> {
                val result = snackbarHostState.showSnackbar(
                    message = status.message,
                    actionLabel = "Retry",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.syncWithGoogleDrive()
                }
                viewModel.resetSyncStatus()
            }
            else -> {}
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLock.endTrustedFlow()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                authManager.handleSignInResult(account)
                viewModel.syncWithGoogleDrive()
            } catch (e: Exception) {
                e.printStackTrace()
                scope.launch {
                    snackbarHostState.showSnackbar("Google Sign-In failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Google Sign-In cancelled or failed")
            }
        }
    }

    passphrasePrompt?.let { prompt ->
        SyncPassphraseDialog(
            isNewPassphrase = prompt.isNewPassphrase,
            onConfirm = { entered ->
                passphrasePrompt = null
                viewModel.setSyncPassphrase(entered)
            },
            onDismiss = { passphrasePrompt = null }
        )
    }

    val pullState = rememberPullToRefreshState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()

    // The device's own 12/24-hour setting. Read here rather than inside the formatter so
    // the formatter stays a pure function with a test.
    val use24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    /**
     * "48 DESIGNS · SYNCED AT 7:45 PM".
     *
     * Three things this deliberately gets right, having got all three wrong first time:
     *
     *  - The count is the WHOLE catalogue, not the filtered view. A number that drops when
     *    you tap a filter is indistinguishable from designs disappearing.
     *  - The time is a real sync completion, not the newest design's edit time. The old
     *    version read "UPDATED 19:08" off the last edit, so it sat frozen while syncs
     *    succeeded behind it - a header reporting success it never actually observed.
     *  - NOT SYNCED YET now means never, not "not since this launch". The time is persisted
     *    (see DataRepository), so a cold start no longer denies a sync that did happen.
     *
     * [syncStamp] carries the rest: a clock time only while it still identifies a moment on
     * its own, and an age or a date once it does not.
     */
    val catalogueStatusLine: (MainScreenUiState, Boolean, Long) -> String = { s, syncing, syncedAt ->
        val total = (s as? MainScreenUiState.Success)?.totalCount ?: 0
        val designs = if (total == 1) "1 DESIGN" else "$total DESIGNS"
        when {
            syncing -> "SYNCING…"
            total == 0 -> "NO DESIGNS YET"
            else -> "$designs · ${syncStamp(syncedAt, System.currentTimeMillis(), use24Hour)}"
        }
    }
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val designsById by viewModel.designsById.collectAsStateWithLifecycle()

    /**
     * "12 ORDERS · 47 PIECES OUT".
     *
     * Counts pieces rather than rupees, because the question this tab answers is what left
     * the workshop. Money is on each order where it belongs to one order.
     */
    val ordersStatusLine: (List<com.sc.biap.data.Order>) -> String = { list ->
        val pieces = list.sumOf { it.pieces }
        when {
            list.isEmpty() -> "NO ORDERS YET"
            else -> "${list.size} ${if (list.size == 1) "ORDER" else "ORDERS"} · " +
                "$pieces ${if (pieces == 1) "PIECE" else "PIECES"} OUT"
        }
    }

    var tab by rememberSaveable { mutableStateOf(MainTab.INVENTORY) }
    var showMenu by remember { mutableStateOf(false) }
    var showMergePrompt by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val profile = rememberPerformanceProfile()

    // BUG-14: surface Google's interactive re-approval instead of silently retrying forever.
    val pendingAuthIntent by authManager.pendingAuthIntent.collectAsStateWithLifecycle()
    val reauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        AppLock.endTrustedFlow()
        authManager.clearPendingAuthIntent()
        viewModel.syncWithGoogleDrive()
    }
    LaunchedEffect(pendingAuthIntent) {
        val intent = pendingAuthIntent
        if (intent != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Google Drive needs re-approval to back up your data.",
                actionLabel = "Approve",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                AppLock.beginTrustedFlow()
                reauthLauncher.launch(intent)
            }
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            if (viewModel.hasGuestData()) {
                showMergePrompt = true
            }
        }
    }

    if (showMergePrompt) {
        AlertDialog(
            onDismissRequest = { showMergePrompt = false },
            title = {
                Text(
                    "Import Offline Designs?",
                    style = MaterialTheme.typography.titleMediumStrong
                )
            },
            text = {
                Text(
                    "We found designs created in your offline guest workspace. Would you like to import them into your Google Account portfolio? This makes them available for cloud backup and syncing across other devices.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMergePrompt = false
                        scope.launch {
                            val success = viewModel.mergeGuestData()
                            if (success) {
                                snackbarHostState.showSnackbar("Offline designs successfully imported!")
                                viewModel.syncWithGoogleDrive()
                            } else {
                                snackbarHostState.showSnackbar("Failed to import offline designs.")
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Import & Sync")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMergePrompt = false
                        showDiscardConfirm = true
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Discard Guest Data")
                }
            },
            shape = AtelierShapes.CornerLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Delete Offline Designs?", style = MaterialTheme.typography.titleMediumStrong) },
            text = { Text("This permanently deletes the designs created in the offline guest workspace. This cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        scope.launch {
                            val cleared = viewModel.discardGuestData()
                            snackbarHostState.showSnackbar(
                                if (cleared) "Offline guest data deleted." else "Could not delete offline guest data."
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Keep")
                }
            },
            shape = AtelierShapes.CornerLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Scaffold(
        topBar = {
            CatalogueHeader(
                title = tab.title,
                statusLine = when (tab) {
                    MainTab.INVENTORY -> catalogueStatusLine(state, isSyncing, lastSyncAt)
                    MainTab.ORDERS -> ordersStatusLine(orders)
                },
                actions = {
                    if (currentUser != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                        ) {
                            // Syncing is a STATE of this button, not a separate spinner that
                            // replaces it - so the control never disappears out from under a
                            // finger already reaching for it.
                            CircleAction(
                                onClick = { if (!isSyncing) viewModel.syncWithGoogleDrive() },
                                container = if (isSyncing) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ) {
                                if (isSyncing) {
                                    if (profile == PerformanceProfile.LOW_POWER) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(SyncSpinnerSmall),
                                            strokeWidth = SyncSpinnerStroke,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        LoadingIndicator(
                                            modifier = Modifier.size(SyncSpinnerLarge),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync now")
                                }
                            }
                            Box {
                                // Sized to HeaderActionSize, matching the sync button beside
                                // it. Not wrapped in an IconButton: that adds its own padding
                                // and shrinks the avatar back inside the touch target, which
                                // is what made it look undersized in the first place.
                                ProfileAvatar(
                                    photoUrl = currentUser?.photoUrl,
                                    size = HeaderActionSize,
                                    tint = LocalContentColor.current,
                                    // Clipped before clickable so the ripple is a circle
                                    // rather than a square behind a round avatar.
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { showMenu = true },
                                )
                                AccountMenu(
                                    expanded = showMenu,
                                    onDismiss = { showMenu = false },
                                    accountLabel = currentUser?.email
                                        ?: appIdentityLabel
                                        ?: "Signed in",
                                    isSyncing = isSyncing,
                                    canManageAccess = myAccessLevel.canManageAccess,
                                    onSync = {
                                        showMenu = false
                                        viewModel.syncWithGoogleDrive()
                                    },
                                    onProfile = {
                                        showMenu = false
                                        onItemClick(Profile)
                                    },
                                    onTeamAccess = {
                                        showMenu = false
                                        onItemClick(AccessManagement)
                                    },
                                    onSettings = {
                                        showMenu = false
                                        onItemClick(Settings)
                                    },
                                    onSignOut = {
                                        showMenu = false
                                        viewModel.signOut {}
                                    },
                                )
                            }
                        }
                    } else {
                        // Drive is not connected. That says nothing about whether the user is
                        // signed IN - a phone or email identity is perfectly valid here - so the
                        // account menu (and Sign Out with it) has to be reachable in this branch
                        // too, not only once a Google account exists.
                        Button(
                            shapes = ButtonDefaults.shapes(shape = CircleShape),
                            onClick = {
                                AppLock.beginTrustedFlow()
                                googleSignInLauncher.launch(authManager.getSignInIntent())
                            },

                            contentPadding = PaddingValues(horizontal = AtelierSpacing.Cozy, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Connect Drive",
                                modifier = Modifier.size(AtelierSpacing.Base)
                            )
                            Spacer(Modifier.width(AtelierSpacing.Micro))
                            Text("Connect Drive", style = MaterialTheme.typography.bodySmallStrong)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Account",
                                    modifier = Modifier.size(AtelierSpacing.Double)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Signed in as:\n${appIdentityLabel ?: "Unknown"}") },
                                    onClick = {},
                                    enabled = false
                                )
                                DropdownMenuItem(
                                    text = { Text("Profile") },
                                    onClick = {
                                        showMenu = false
                                        onItemClick(Profile)
                                    }
                                )
                                if (myAccessLevel.canManageAccess) {
                                    DropdownMenuItem(
                                        text = { Text("Team Access") },
                                        onClick = {
                                            showMenu = false
                                            onItemClick(AccessManagement)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Sign Out") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.signOut {}
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Extended and labelled, in the saturated primary role. A bare "+" is a guess;
            // each tab has one obvious thing to add and can afford to say which.
            ExtendedFloatingActionButton(
                onClick = {
                    onItemClick(if (tab == MainTab.INVENTORY) AddDesign else NewOrder())
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.height(64.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(AtelierSpacing.Compact))
                Text(
                    if (tab == MainTab.INVENTORY) "Add design" else "New order",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        bottomBar = { MainNavigationBar(selected = tab, onSelect = { tab = it }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when (state) {
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            is MainScreenUiState.Success -> {
                val success = state as MainScreenUiState.Success
                val data = success.data

                // Pull to refresh AND the toolbar button, not one or the other. Pull is the
                // gesture people reach for; the button is the only way to sync a catalogue
                // that is empty and therefore has nothing to pull.
                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = { viewModel.syncWithGoogleDrive() },
                    state = pullState,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    indicator = {
                        // The Expressive indicator, which morphs through a sequence of shapes,
                        // rather than the legacy circular spinner.
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = isSyncing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                ) {
                    when (tab) {
                        MainTab.INVENTORY -> Column(modifier = Modifier.fillMaxSize()) {
                            CatalogueSearchField(
                                query = searchQuery,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )

                            if (data.designs.isEmpty()) {
                                CatalogueEmptyState(
                                    query = searchQuery,
                                    filters = success.filters,
                                    onAddDesign = { onItemClick(AddDesign) },
                                    onClearFilters = { viewModel.clearFilters() },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                CatalogueList(
                                    designs = data.designs,
                                    onOpenDesign = { onItemClick(DesignDetail(it.id)) },
                                    onOpenSize = { design, size ->
                                        onItemClick(DesignDetail(design.id, focusSize = size.size))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AttentionBand(
                                            summary = success.summary,
                                            isActive = success.filters.attentionOnly,
                                            onClick = { viewModel.toggleAttentionFilter() },
                                        )
                                        CatalogueFilterRow(
                                            filters = success.filters,
                                            categories = success.categories,
                                            archivedCount = success.archivedCount,
                                            onToggleAttention = { viewModel.toggleAttentionFilter() },
                                            onToggleArchived = { viewModel.toggleArchivedFilter() },
                                            onSelectCategory = { viewModel.selectCategory(it) },
                                            modifier = Modifier.padding(horizontal = 0.dp),
                                        )
                                    }
                                }
                            }
                        }

                        MainTab.ORDERS -> if (orders.isEmpty()) {
                            OrdersEmptyState(
                                // Reads the whole catalogue, not the filtered view: whether
                                // there is anything to order has nothing to do with what the
                                // Inventory tab is currently narrowed to.
                                hasDesigns = designsById.isNotEmpty(),
                                onNewOrder = { onItemClick(NewOrder()) },
                            )
                        } else {
                            OrdersList(
                                orders = orders,
                                designs = designsById,
                                onEdit = { onItemClick(NewOrder(orderId = it.id)) },
                                onCancel = { viewModel.cancelOrder(it.id) },
                            )
                        }
                    }
                }
            }
            is MainScreenUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Error loading data: ${(state as MainScreenUiState.Error).throwable.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
