package com.sc.biap.ui.design

/**
 * @file AddDesignScreen.kt
 * @description Capture form for a brand-new design: photo, details, size matrix,
 *              karigars and custom attributes.
 *
 * READ THIS FIRST:
 *   AddDesignScreen itself is only an orchestrator - it owns the form-wide concerns
 *   (photo launchers, unsaved-changes guard, save navigation) and then calls one
 *   composable per card. Each card below is self-contained and takes plain values
 *   plus callbacks, never the ViewModel, so it can be read, changed or previewed on
 *   its own without holding the whole screen in your head.
 *
 * [TABLE OF CONTENTS]
 * 1. IMPORTS & DEPENDENCIES
 * 2. LOCAL CONSTANTS & GEOMETRY
 * 3. MAIN SCREEN (orchestrator)
 * 4. TOP BAR
 * 5. PHOTO
 * 6. BASIC DETAILS
 * 7. SIZES, STOCK & PRICING
 * 8. KARIGARS
 * 9. ATTRIBUTES & CUSTOM FIELDS
 * 10. DIALOGS
 * 11. HELPER UTILITIES
 */

// #region 1. Imports & Dependencies
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.DesignPhoto
import com.sc.biap.data.SizeInventory
import com.sc.biap.security.AppLock
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.horizontalFadingEdges
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.titleLargeStrong
import com.sc.biap.theme.titleMediumStrong
import com.sc.biap.util.FileUtils
import kotlinx.coroutines.launch
// #endregion

// #region 2. Local Constants & Geometry

// Artwork and control geometry: intentionally not on the AtelierSpacing scale, which
// governs layout rhythm rather than the intrinsic size of images, icons and chips.
private val ImagePickerHeight = 200.dp
private val OverlayIconSize = 20.dp
private val EmptyStateIconSize = 40.dp
private val SizeTokenWidth = 56.dp
private val RemoveIconSize = 18.dp
private val TabVerticalPadding = 6.dp

/** Input ceilings, kept together so the limits are reviewable at a glance. */
private const val MAX_NAME_LENGTH = 80
private const val MAX_CATEGORY_LENGTH = 80
private const val MAX_SIZE_LABEL_LENGTH = 10
private const val MAX_KARIGAR_NAME_LENGTH = 50
private const val MAX_FIELD_NAME_LENGTH = 50
private const val MAX_FIELD_VALUE_LENGTH = 200
private const val MAX_STOCK_DIGITS = 6
private const val MAX_PRICE_CHARS = 9
private const val MAX_PRICE_DECIMALS = 2

/** A three-piece outfit is described garment by garment; "General" holds the rest. */
private val AttributeTabs = listOf("Top (Kameez)", "Bottom (Salwar)", "Dupatta", "General")
private val ScopeLabels = listOf("Top", "Bottom", "Dupatta", "General")
private const val GENERAL_SCOPE_INDEX = 3

/**
 * Custom-field keys are namespaced by garment with a prefix, so "Fabric" can exist
 * independently on the top, the bottom and the dupatta. "General" carries no prefix.
 */
private fun scopePrefix(index: Int): String = when (index) {
    0 -> "Top - "
    1 -> "Bottom - "
    2 -> "Dupatta - "
    else -> ""
}

/** Seeded on every design, so these cannot be removed - only left blank. */
private val DefaultAttributeFields = setOf(
    "Top - Fabric", "Top - Work", "Top - Color",
    "Bottom - Fabric", "Bottom - Work", "Bottom - Color",
    "Dupatta - Fabric", "Dupatta - Work", "Dupatta - Color"
)
// #endregion

// #region 3. Main Screen (orchestrator)

@Composable
private fun defaultAddDesignViewModel(): AddDesignViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { AddDesignViewModel(DefaultDataRepository.getInstance(context)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddDesignScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddDesignViewModel = defaultAddDesignViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourceSelector by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Survives process death: the camera can evict us while it is in the foreground,
    // and on return we still need to know which file it was told to write into.
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempPhotoUri = remember(tempPhotoUriString) {
        tempPhotoUriString?.let { Uri.parse(it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        AppLock.endTrustedFlow()
        if (uri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, uri)
                viewModel.onImageSelected(localPath)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        AppLock.endTrustedFlow()
        if (success && tempPhotoUri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, tempPhotoUri)
                viewModel.onImageSelected(localPath)
            }
        }
    }

    // Anything the user has actually typed or chosen counts as work worth protecting.
    val isFormDirty = remember(state) {
        state.name.isNotEmpty() ||
            state.category != AddDesignViewModel.DEFAULT_CATEGORY ||
            state.photoPaths.isNotEmpty() ||
            state.sizes.isNotEmpty() ||
            state.karigars.isNotEmpty() ||
            state.customFields.values.any { it.isNotEmpty() }
    }

    val requestBack = {
        if (isFormDirty) showUnsavedChangesDialog = true else onBack()
    }

    BackHandler(onBack = requestBack)

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            // Consume BEFORE navigating: the flag must not outlive the navigation it caused.
            viewModel.onSaveNavigationHandled()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            AddDesignTopBar(
                canSave = state.name.isNotBlank(),
                onBack = requestBack,
                onSave = viewModel::saveDesign
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Large)
        ) {
            if (state.photoPaths.isEmpty()) {
                DesignPhotoEmptyPanel(onClick = { showSourceSelector = true })
            } else {
                DesignPhotoStrip(
                    photos = state.photoPaths.map { DesignPhoto(localPath = it) },
                    editable = true,
                    onAdd = { showSourceSelector = true },
                    onRemove = { viewModel.onImageRemoved(it) },
                )
            }

            BasicDetailsCard(
                name = state.name,
                category = state.category,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange
            )

            SizeScaleAndPricingCard(
                availableSizes = state.availableSizes,
                sizes = state.sizes,
                onUpdateSize = viewModel::updateSizeInventory,
                onAddAvailableSize = viewModel::addAvailableSize
            )

            KarigarAssignmentCard(
                karigars = state.karigars,
                onAddKarigar = viewModel::addKarigar,
                onRemoveKarigar = viewModel::removeKarigar
            )

            AttributesAndCustomFieldsCard(
                customFields = state.customFields,
                globalCustomFields = state.globalCustomFields,
                onValueChange = viewModel::updateCustomFieldValue,
                onRemoveField = viewModel::removeCustomField,
                onAddField = viewModel::addCustomField
            )
        }
    }

    if (showUnsavedChangesDialog) {
        DiscardChangesDialog(
            onDiscard = {
                showUnsavedChangesDialog = false
                onBack()
            },
            onKeepEditing = { showUnsavedChangesDialog = false }
        )
    }

    if (showSourceSelector) {
        PhotoSourceDialog(
            onDismiss = { showSourceSelector = false },
            onPickFromGallery = {
                showSourceSelector = false
                // The picker takes the screen; returning from it is not "the user left".
                AppLock.beginTrustedFlow()
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                showSourceSelector = false
                try {
                    val uri = FileUtils.createCameraTempUri(context)
                    tempPhotoUriString = uri.toString()
                    AppLock.beginTrustedFlow()
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    AppLock.endTrustedFlow()
                    e.printStackTrace()
                }
            }
        )
    }
}
// #endregion

// #region 4. Top Bar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddDesignTopBar(
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    TopAppBar(
        title = { Text("Add New Design", style = MaterialTheme.typography.titleLargeStrong) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            Button(
                onClick = onSave,
                enabled = canSave,
                shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Save",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(AtelierSpacing.Micro))
                Text("Save")
            }
        }
    )
}
// #endregion

// #region 5. Photo

/*
 * The old single-photo panel lived here. Replaced by DesignPhotoStrip and
 * DesignPhotoEmptyPanel in DesignPhotos.kt, which the detail screen shares.
 */
// #endregion

// #region 6. Basic Details

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BasicDetailsCard(
    name: String,
    category: String,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
        ) {
            Text("Basic Details", style = MaterialTheme.typography.titleMediumStrong)

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_NAME_LENGTH) onNameChange(it) },
                label = { Text("Design Name / Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AtelierShapes.CornerMedium
            )

            OutlinedTextField(
                value = category,
                onValueChange = { if (it.length <= MAX_CATEGORY_LENGTH) onCategoryChange(it) },
                label = { Text("Category (e.g. Salwar Kameez)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AtelierShapes.CornerMedium
            )
        }
    }
}
// #endregion

// #region 7. Sizes, Stock & Pricing

/**
 * The size matrix. Turning a size on reveals its own stock and price row, because
 * an atelier prices per size - an XXL is not the same money as an S.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizeScaleAndPricingCard(
    availableSizes: List<String>,
    sizes: List<SizeInventory>,
    onUpdateSize: (String, Int, Long, Boolean, Boolean) -> Unit,
    onAddAvailableSize: (String) -> Unit
) {
    var showCustomSizeDialog by remember { mutableStateOf(false) }
    var sizeToRemove by remember { mutableStateOf<String?>(null) }

    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Sizes, Stock & Prices", style = MaterialTheme.typography.titleMediumStrong)
            Text(
                "Configure stock and unique pricing per size.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Configure Size Scale", style = MaterialTheme.typography.bodyMediumStrong)

            SizeScaleChipRow(
                availableSizes = availableSizes,
                sizes = sizes,
                onToggleSize = { size, enable ->
                    if (enable) {
                        onUpdateSize(size, 0, 0L, true, true)
                    } else {
                        // Only interrupt when there is something to lose.
                        val inventory = sizes.find { it.size == size }
                        if (inventory != null && (inventory.stock > 0 || inventory.price > 0L)) {
                            sizeToRemove = size
                        } else {
                            onUpdateSize(size, 0, 0L, false, false)
                        }
                    }
                },
                onAddCustom = { showCustomSizeDialog = true }
            )

            val enabledSizes = availableSizes.filter { size -> sizes.any { it.size == size } }

            if (enabledSizes.isEmpty()) {
                Text(
                    "Select sizes above to configure stock and unique pricing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AtelierSpacing.Cozy)
                )
            } else {
                enabledSizes.forEach { size ->
                    SizeInventoryRow(
                        size = size,
                        inventory = sizes.find { it.size == size },
                        onChange = { stock, price -> onUpdateSize(size, stock, price, true, false) }
                    )
                }
            }
        }
    }

    sizeToRemove?.let { size ->
        RemoveSizeDialog(
            size = size,
            onConfirm = {
                onUpdateSize(size, 0, 0L, false, false)
                sizeToRemove = null
            },
            onDismiss = { sizeToRemove = null }
        )
    }

    if (showCustomSizeDialog) {
        CustomSizeDialog(
            availableSizes = availableSizes,
            onAdd = { normalizedSize ->
                onAddAvailableSize(normalizedSize)
                onUpdateSize(normalizedSize, 0, 0L, true, true)
                showCustomSizeDialog = false
            },
            onDismiss = { showCustomSizeDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizeScaleChipRow(
    availableSizes: List<String>,
    sizes: List<SizeInventory>,
    onToggleSize: (size: String, enable: Boolean) -> Unit,
    onAddCustom: () -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalFadingEdges(scrollState)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableSizes.forEach { size ->
            val isEnabled = sizes.any { it.size == size }
            FilterChip(
                selected = isEnabled,
                onClick = { onToggleSize(size, !isEnabled) },
                label = { Text(size, style = MaterialTheme.typography.labelLargeStrong) },
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }

        InputChip(
            selected = false,
            onClick = onAddCustom,
            label = { Text("+ Custom") },
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}

/**
 * One size's stock and price.
 *
 * Both fields keep a local text buffer rather than binding straight to the model.
 * Money is stored as whole paise, but a user types "1", then "1.", then "1.2" -
 * intermediate states that are not yet a number. The buffer holds exactly what was
 * typed while only well-formed values are pushed upward, so a half-typed price can
 * never be read back as a different amount.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizeInventoryRow(
    size: String,
    inventory: SizeInventory?,
    onChange: (stock: Int, priceInPaise: Long) -> Unit
) {
    var stockText by remember(inventory) { mutableStateOf(inventory?.stock?.toString() ?: "") }

    var priceText by remember {
        val initialText =
            if (inventory == null || inventory.price == 0L) "" else (inventory.price / 100.0).toString()
        mutableStateOf(initialText)
    }

    // Re-sync only when the stored amount genuinely differs from what is on screen,
    // so typing is never fought by an echo of the user's own keystrokes.
    LaunchedEffect(inventory?.price) {
        val currentPrice = inventory?.price ?: 0L
        val parsedDouble = priceText.toDoubleOrNull() ?: 0.0
        val parsedPaise = (parsedDouble * 100).toLong()
        if (parsedPaise != currentPrice) {
            priceText = if (currentPrice == 0L) "" else (currentPrice / 100.0).toString()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AtelierSpacing.Micro),
        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        Box(
            modifier = Modifier
                .width(SizeTokenWidth)
                .background(MaterialTheme.colorScheme.primaryContainer, AtelierShapes.CornerSmall)
                .padding(horizontal = AtelierSpacing.Compact, vertical = AtelierSpacing.Compact),
            contentAlignment = Alignment.Center
        ) {
            Text(
                size,
                style = MaterialTheme.typography.labelLargeStrong,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        OutlinedTextField(
            value = stockText,
            onValueChange = { newValue ->
                val digitsOnly = newValue.filter { it.isDigit() }
                if (digitsOnly.length <= MAX_STOCK_DIGITS) {
                    stockText = digitsOnly
                    onChange(digitsOnly.toIntOrNull() ?: 0, FileUtils.parsePriceToCents(priceText))
                }
            },
            label = { Text("Stock") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            shape = AtelierShapes.CornerSmall
        )

        OutlinedTextField(
            value = priceText,
            onValueChange = { newValue ->
                val dotCount = newValue.count { it == '.' }
                val sanitized = newValue.filter { it.isDigit() || it == '.' }
                if (dotCount <= 1 && sanitized.length <= MAX_PRICE_CHARS) {
                    val dotIndex = sanitized.indexOf('.')
                    if (dotIndex == -1 || sanitized.length - dotIndex - 1 <= MAX_PRICE_DECIMALS) {
                        priceText = sanitized
                        onChange(stockText.toIntOrNull() ?: 0, FileUtils.parsePriceToCents(sanitized))
                    }
                }
            },
            label = { Text("Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            shape = AtelierShapes.CornerSmall,
            prefix = { Text("₹") }
        )
    }
}
// #endregion

// #region 8. Karigars

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KarigarAssignmentCard(
    karigars: List<String>,
    onAddKarigar: (String) -> Unit,
    onRemoveKarigar: (String) -> Unit
) {
    var newKarigarName by remember { mutableStateOf("") }

    val commit = {
        if (newKarigarName.isNotBlank()) {
            onAddKarigar(newKarigarName.trim())
            newKarigarName = ""
        }
    }

    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Assigned Karigars / Tailors", style = MaterialTheme.typography.titleMediumStrong)
            Text(
                "Mention the karigars who worked on this design.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = newKarigarName,
                onValueChange = { if (it.length <= MAX_KARIGAR_NAME_LENGTH) newKarigarName = it },
                label = { Text("Karigar Name (e.g. Master Salim)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AtelierShapes.CornerMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                trailingIcon = {
                    IconButton(
                        onClick = commit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Karigar")
                    }
                }
            )

            if (karigars.isEmpty()) {
                Text(
                    "No Karigars assigned yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AtelierSpacing.Micro)
                )
            } else {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdges(scrollState)
                        .horizontalScroll(scrollState)
                        .padding(vertical = AtelierSpacing.Micro),
                    horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    karigars.forEach { karigar ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(karigar, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = {
                                // The tap target is the Box, not the glyph: the icon
                                // stays 18dp visually while the touch area meets the
                                // 48dp minimum. This fits inside the chip's existing
                                // minimumInteractiveComponentSize height, so the chip
                                // does not grow taller.
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Remove"
                                        ) { onRemoveKarigar(karigar) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Remove $karigar",
                                        modifier = Modifier.size(RemoveIconSize)
                                    )
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }
        }
    }
}
// #endregion

// #region 9. Attributes & Custom Fields

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttributesAndCustomFieldsCard(
    customFields: Map<String, String>,
    globalCustomFields: Set<String>,
    onValueChange: (String, String) -> Unit,
    onRemoveField: (String) -> Unit,
    onAddField: (String, Boolean) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
        ) {
            Text(
                "3-Piece Attributes & Custom Fields",
                style = MaterialTheme.typography.titleMediumStrong
            )

            AttributeScopeTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )

            val prefix = scopePrefix(selectedTab)
            val filteredFields = customFields.filter { (key, _) ->
                if (selectedTab < GENERAL_SCOPE_INDEX) {
                    key.startsWith(prefix)
                } else {
                    // "General" is everything that belongs to no single garment.
                    !key.startsWith("Top - ") &&
                        !key.startsWith("Bottom - ") &&
                        !key.startsWith("Dupatta - ")
                }
            }

            if (filteredFields.isEmpty()) {
                Text(
                    "No attributes set in this category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AtelierSpacing.Cozy)
                )
            } else {
                filteredFields.forEach { (key, value) ->
                    AttributeFieldRow(
                        label = if (selectedTab < GENERAL_SCOPE_INDEX) key.removePrefix(prefix) else key,
                        value = value,
                        isGlobal = globalCustomFields.contains(key),
                        isRemovable = !DefaultAttributeFields.contains(key),
                        onValueChange = { onValueChange(key, it) },
                        onRemove = { onRemoveField(key) }
                    )
                }
            }

            HorizontalDivider()

            CustomFieldComposer(onAddField = onAddField)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttributeScopeTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = AtelierSpacing.None,
        indicator = {},
        divider = {}
    ) {
        AttributeTabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Tab(
                selected = isSelected,
                onClick = { onSelectTab(index) },
                text = {
                    Text(
                        text = title,
                        maxLines = 1,
                        style = if (isSelected) {
                            MaterialTheme.typography.labelLargeStrong
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                modifier = Modifier
                    .padding(horizontal = AtelierSpacing.Micro, vertical = TabVerticalPadding)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttributeFieldRow(
    label: String,
    value: String,
    isGlobal: Boolean,
    isRemovable: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= MAX_FIELD_VALUE_LENGTH) onValueChange(it) },
            label = { Text(if (isGlobal) "$label (Global)" else label) },
            modifier = Modifier.weight(1f),
            shape = AtelierShapes.CornerMedium
        )

        if (isRemovable) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Remove")
            }
        }
    }
}

/** Composes a new attribute: name, which garment it belongs to, and whether it is global. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomFieldComposer(
    onAddField: (String, Boolean) -> Unit
) {
    var newKey by remember { mutableStateOf("") }
    var isGlobalToggle by remember { mutableStateOf(false) }
    var selectedScopeIndex by remember { mutableStateOf(0) }

    Text("Add New Custom Field", style = MaterialTheme.typography.bodyMediumStrong)

    OutlinedTextField(
        value = newKey,
        onValueChange = { if (it.length <= MAX_FIELD_NAME_LENGTH) newKey = it },
        label = { Text("Field Name (e.g. Sleeve Style, Lining)") },
        modifier = Modifier.fillMaxWidth(),
        shape = AtelierShapes.CornerMedium
    )

    Text(
        "Apply Field To:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        ScopeLabels.forEachIndexed { idx, scopeName ->
            FilterChip(
                selected = selectedScopeIndex == idx,
                onClick = { selectedScopeIndex = idx },
                label = { Text(scopeName) },
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Make Global Field", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Appears on all products by default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isGlobalToggle,
            onCheckedChange = { isGlobalToggle = it },
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Button(
            onClick = {
                if (newKey.isNotBlank()) {
                    onAddField(scopePrefix(selectedScopeIndex) + newKey.trim(), isGlobalToggle)
                    newKey = ""
                    isGlobalToggle = false
                }
            },
            modifier = Modifier.minimumInteractiveComponentSize(),
            shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
            Spacer(Modifier.width(AtelierSpacing.Micro))
            Text("Add Field")
        }
    }
}
// #endregion

// #region 10. Dialogs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard Design?") },
        text = {
            Text("You have unsaved changes that will be lost. Are you sure you want to discard them?")
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onKeepEditing) { Text("Keep Editing") }
        }
    )
}

/** Gallery or camera. Kept as a dialog so the choice is explicit rather than a guess. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoSourceDialog(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Image Source", style = MaterialTheme.typography.titleLargeStrong) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)) {
                Button(
                    onClick = onPickFromGallery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                    shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium)) {
                    Text("Choose from Gallery")
                }

                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                    shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium)) {
                    Text("Take Photo with Camera")
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoveSizeDialog(
    size: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Size $size?") },
        text = {
            Text("This will clear the price and stock values configured for this size. Are you sure you want to remove it?")
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomSizeDialog(
    availableSizes: List<String>,
    onAdd: (normalizedSize: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newSizeInput by remember { mutableStateOf("") }

    // Sizes are matched case-insensitively, so "xl" and "XL" are the same size.
    val normalizedInput = newSizeInput.trim().uppercase()
    val sizeExists = remember(newSizeInput, availableSizes) {
        normalizedInput.isNotEmpty() && availableSizes.contains(normalizedInput)
    }
    val canAdd = newSizeInput.isNotBlank() && !sizeExists

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Size", style = MaterialTheme.typography.titleLargeStrong) },
        text = {
            OutlinedTextField(
                value = newSizeInput,
                onValueChange = { if (it.length <= MAX_SIZE_LABEL_LENGTH) newSizeInput = it },
                label = { Text("Size Label (e.g. 3XL, 6XL, 42)") },
                singleLine = true,
                shape = AtelierShapes.CornerSmall,
                isError = sizeExists,
                supportingText = if (sizeExists) {
                    { Text("This size is already configured!", color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(shapes = ButtonDefaults.shapes(), onClick = { if (canAdd) onAdd(normalizedInput) },
                enabled = canAdd
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") }
        }
    )
}
// #endregion

// #region 11. Helper Utilities

/** Every card on this form shares one silhouette; defined once so they cannot drift apart. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}
// #endregion
