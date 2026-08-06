package com.sc.biap.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.sc.biap.data.SyncStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.AddDesign
import com.sc.biap.DesignDetail
import com.sc.biap.data.BusinessIntelligenceData
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.Design
import com.sc.biap.theme.AtelierTheme
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.bodySmallStrong
import com.sc.biap.theme.glowBrush
import com.sc.biap.theme.titleMediumStrong
import java.io.File
import com.sc.biap.util.FileUtils
import com.sc.biap.ui.rememberPerformanceProfile
import com.sc.biap.ui.PerformanceProfile
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun defaultMainScreenViewModel(): MainScreenViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { MainScreenViewModel(DefaultDataRepository.getInstance(context)) }
}

// Component metrics and artwork geometry: intrinsic sizes, deliberately off the
// AtelierSpacing scale, which governs layout rhythm rather than component size.
private val SyncSpinnerSmall = 20.dp
private val SyncSpinnerLarge = 28.dp
private val SyncSpinnerStroke = 2.dp
private val EmptyStateGlowSize = 96.dp
private val EmptyStateIconSize = 48.dp
private val EmptyStateGap = 20.dp
private val EmptyStateTextWidth = 280.dp
private val DesignCardImageHeight = 140.dp

@OptIn(ExperimentalMaterial3Api::class)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(syncStatus) {
        when (val status = syncStatus) {
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

    var showMenu by remember { mutableStateOf(false) }
    var showMergePrompt by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val profile = rememberPerformanceProfile()

    // BUG-14: surface Google's interactive re-approval instead of silently retrying forever.
    val pendingAuthIntent by authManager.pendingAuthIntent.collectAsStateWithLifecycle()
    val reauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
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
                    shape = AtelierShapes.CornerMedium,
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
            TopAppBar(
                title = { Text("Product Portfolio", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (currentUser != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                        ) {
                            if (isSyncing) {
                                if (profile == PerformanceProfile.LOW_POWER) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(SyncSpinnerSmall),
                                        strokeWidth = SyncSpinnerStroke,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(SyncSpinnerLarge),
                                        strokeWidth = SyncSpinnerStroke,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(onClick = { viewModel.syncWithGoogleDrive() }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync Now")
                                }
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    if (currentUser?.photoUrl != null) {
                                        Image(
                                            painter = rememberAsyncImagePainter(currentUser!!.photoUrl),
                                            contentDescription = "Profile",
                                            modifier = Modifier
                                                .size(AtelierSpacing.Double)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.size(AtelierSpacing.Double))
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Signed in as:\n${currentUser?.email}") },
                                        onClick = {},
                                        enabled = false
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sync with Drive") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.syncWithGoogleDrive()
                                        }
                                    )
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
                    } else {
                        // Drive is not connected. That says nothing about whether the user is
                        // signed IN - a phone or email identity is perfectly valid here - so the
                        // account menu (and Sign Out with it) has to be reachable in this branch
                        // too, not only once a Google account exists.
                        Button(
                            onClick = { googleSignInLauncher.launch(authManager.getSignInIntent()) },
                            shape = CircleShape,
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
            FloatingActionButton(
                onClick = { onItemClick(AddDesign) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = AtelierShapes.CornerLarge
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Design")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when (state) {
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MainScreenUiState.Success -> {
                val data = (state as MainScreenUiState.Success).data
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    if (isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    SearchBarField(
                        query = searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) }
                    )
                    MainScreenContent(
                        data = data,
                        query = searchQuery,
                        onItemClick = onItemClick,
                        modifier = Modifier.weight(1f)
                    )
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

@Composable
internal fun MainScreenContent(
    data: BusinessIntelligenceData,
    query: String,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    if (data.designs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(AtelierSpacing.Large)
            ) {
                Box(
                    modifier = Modifier
                        .size(EmptyStateGlowSize)
                        .background(
                            brush = MaterialTheme.colorScheme.glowBrush,
                            shape = CircleShape,
                            alpha = 0.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (query.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Inventory,
                        contentDescription = "Empty stock",
                        modifier = Modifier.size(EmptyStateIconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(EmptyStateGap))
                Text(
                    text = if (query.isNotEmpty()) "No results found" else "No designs added yet",
                    style = MaterialTheme.typography.titleMediumStrong
                )
                Spacer(Modifier.height(AtelierSpacing.Compact))
                Text(
                    text = if (query.isNotEmpty()) {
                        "We couldn't find anything matching \"$query\". Check for typos or search by category, size, or material."
                    } else {
                        "Tap the + button to upload design photos, set prices by size, and add custom fields."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(EmptyStateTextWidth),
                    onTextLayout = {},
                    textAlign = TextAlign.Center
                )
                if (query.isEmpty()) {
                    Spacer(Modifier.height(AtelierSpacing.Large))
                    Button(
                        onClick = { onItemClick(AddDesign) },
                        shape = AtelierShapes.CornerMedium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(AtelierSpacing.Compact))
                        Text("Add Your First Design")
                    }
                }
            }
        }
    } else {
        val configuration = LocalConfiguration.current
        val columnsCount = when {
            configuration.screenWidthDp < 600 -> 2
            configuration.screenWidthDp < 840 -> 3
            else -> 4
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(AtelierSpacing.Base),
            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
        ) {
            items(data.designs) { design ->
                DesignCard(design = design, onClick = { onItemClick(DesignDetail(design.id)) })
            }
        }
    }
}

@Composable
fun DesignCard(design: Design, onClick: () -> Unit) {
    val totalStock = design.sizeInventories.sumOf { it.stock }
    val prices = design.sizeInventories.map { it.price }
    val priceText = when {
        prices.isEmpty() -> "No Price"
        prices.size == 1 -> "₹${FileUtils.formatPrice(prices.first())}"
        else -> "₹${FileUtils.formatPrice(prices.minOrNull() ?: 0L)} - ₹${FileUtils.formatPrice(prices.maxOrNull() ?: 0L)}"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val profile = rememberPerformanceProfile()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )
    val scale = if (profile == PerformanceProfile.LOW_POWER) {
        if (isPressed) 0.95f else 1f
    } else {
        animatedScale
    }


    Card(
        shape = AtelierShapes.CornerLarge,
        border = BorderStroke(AtelierSpacing.Hairline, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DesignCardImageHeight)
                    .clip(RoundedCornerShape(topStart = AtelierSpacing.Base, topEnd = AtelierSpacing.Base))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (design.imagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(FileUtils.resolveImageFile(LocalContext.current, design.imagePath)),
                        contentDescription = design.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = "No photo",
                        modifier = Modifier.size(AtelierSpacing.Double),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(AtelierSpacing.Cozy),
                verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Micro)
            ) {
                Text(
                    text = design.name,
                    style = MaterialTheme.typography.titleMediumStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = design.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = priceText,
                        style = MaterialTheme.typography.bodyMediumStrong
                    )

                    Text(
                        text = "$totalStock items",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (totalStock == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search by name, fabric, color, size...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AtelierSpacing.Base, vertical = AtelierSpacing.Compact),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = AtelierShapes.CornerMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
