package com.sc.biap.ui.design

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import com.sc.biap.ui.rememberDesignImageModel
import com.sc.biap.util.FileUtils
import com.sc.biap.util.SizeUtils
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.headlineSmallStrong
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.success
import com.sc.biap.theme.titleLargeStrong
import com.sc.biap.theme.titleMediumStrong
import com.sc.biap.theme.warning
import com.sc.biap.theme.horizontalFadingEdges
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun defaultDesignDetailViewModel(designId: String): DesignDetailViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { DesignDetailViewModel(designId, DefaultDataRepository.getInstance(context)) }
}

// Stock at or below this count reads as low rather than healthy.
private const val LOW_STOCK_THRESHOLD = 5

// Artwork and control geometry: intrinsic sizes, deliberately off the AtelierSpacing
// scale, which governs layout rhythm rather than how big an image or glyph is.
private val HeroImageHeight = 280.dp
private val SizeTokenWidth = 56.dp
private val EmptyStateIconSize = 40.dp
private val OverlayIconSize = 20.dp
private val RemoveIconSize = 18.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignDetailScreen(
    designId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DesignDetailViewModel = defaultDesignDetailViewModel(designId)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Edit states
    var editName by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }
    var editImagePath by remember { mutableStateOf<String?>(null) }
    var editSizes by remember { mutableStateOf<List<SizeInventory>>(emptyList()) }
    var editCustomFields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editKarigars by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEditKarigarsDialog by remember { mutableStateOf(false) }

    var showSourceSelector by remember { mutableStateOf(false) }
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempPhotoUri = remember(tempPhotoUriString) {
        tempPhotoUriString?.let { Uri.parse(it) }
    }
    var editBaseUpdatedAt by rememberSaveable { mutableStateOf(0L) }
    var showConflictDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, uri)
                editImagePath = localPath
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoUri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, tempPhotoUri!!)
                editImagePath = localPath
            }
        }
    }

    if (showDeleteDialog) {
        DeleteDesignDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirmDelete = {
                // Order is load-bearing: deleteDesign() flips uiState to NotFound on the
                // next emission, so it must run before the dialog is dismissed and before
                // navigating back.
                viewModel.deleteDesign()
                showDeleteDialog = false
                onBack()
            }
        )
    }

    when (val uiState = state) {
        DesignDetailUiState.Loading -> {
            DetailStatusScaffold(title = "Loading...", onBack = onBack, modifier = modifier) {
                CircularProgressIndicator()
            }
        }
        DesignDetailUiState.NotFound -> {
            DetailStatusScaffold(title = "Not Found", onBack = onBack, modifier = modifier) {
                Text("Design not found", style = MaterialTheme.typography.titleMedium)
            }
        }
        is DesignDetailUiState.Success -> {
            val design = uiState.design

            // Sync with actual design when edit mode is activated
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    editBaseUpdatedAt = design.updatedAt
                    editName = design.name
                    editCategory = design.category
                    editImagePath = design.imagePath
                    editSizes = design.sizeInventories
                    editCustomFields = design.customFields
                    editKarigars = design.karigars
                }
            }

            if (showConflictDialog) {
                SaveConflictDialog(
                    onDismiss = { showConflictDialog = false },
                    onOverwrite = {
                        showConflictDialog = false
                        val updated = design.copy(
                            name = editName.trim(),
                            category = editCategory.trim(),
                            imagePath = editImagePath,
                            imageRef = if (editImagePath != design.imagePath) null else design.imageRef,
                            sizeInventories = editSizes,
                            customFields = editCustomFields.filterKeys { it.isNotBlank() },
                            karigars = editKarigars
                        )
                        viewModel.updateDesign(updated)
                        isEditing = false
                    }
                )
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (isEditing) "Edit Design" else "Design Details", style = MaterialTheme.typography.titleLargeStrong) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isEditing) {
                                    isEditing = false
                                } else {
                                    onBack()
                                }
                            }) {
                                Icon(
                                    if (isEditing) Icons.Default.Clear else Icons.Default.ArrowBack,
                                    contentDescription = if (isEditing) "Cancel" else "Back"
                                )
                            }
                        },
                        actions = {
                            if (isEditing) {
                                IconButton(
                                    onClick = {
                                        if (editName.isNotBlank()) {
                                            if (design.updatedAt != editBaseUpdatedAt) {
                                                showConflictDialog = true
                                            } else {
                                                val updated = design.copy(
                                                    name = editName.trim(),
                                                    category = editCategory.trim(),
                                                    imagePath = editImagePath,
                                                    // A different local photo means the
                                                    // uploaded one is superseded. Clearing the
                                                    // pointer is what marks it for re-upload;
                                                    // the previous object is left intact until
                                                    // the new bytes land.
                                                    imageRef = if (editImagePath != design.imagePath) null else design.imageRef,
                                                    sizeInventories = editSizes,
                                                    customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                                    karigars = editKarigars
                                                )
                                                viewModel.updateDesign(updated)
                                                isEditing = false
                                            }
                                        }
                                    },
                                    enabled = editName.isNotBlank()
                                ) {
                                    Icon(
                                        Icons.Default.Done,
                                        contentDescription = "Save",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                IconButton(onClick = { isEditing = true }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Design",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
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
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    DesignImageCard(
                        design = design,
                        isEditing = isEditing,
                        editImagePath = editImagePath,
                        onPickImage = { showSourceSelector = true },
                    )

                    TitleCategoryCard(
                        design = design,
                        isEditing = isEditing,
                        editName = editName,
                        editCategory = editCategory,
                        onNameChange = { editName = it },
                        onCategoryChange = { editCategory = it },
                    )

                    // Inventory & Pricing Card
                    if (isEditing) {
                        SizeStockPriceEditorCard(
                            editSizes = editSizes,
                            onEditSizesChange = { editSizes = it },
                        )
                    } else {
                        InventoryPricingCard(design = design)
                    }

                    AssignedKarigarsCard(
                        design = design,
                        isEditing = isEditing,
                        editKarigars = editKarigars,
                        onManageKarigars = { showEditKarigarsDialog = true },
                    )

                    if (showEditKarigarsDialog) {
                        KarigarEditorDialog(
                            design = design,
                            isEditing = isEditing,
                            editKarigars = editKarigars,
                            viewModel = viewModel,
                            onEditKarigarsChange = { editKarigars = it },
                            onDismiss = { showEditKarigarsDialog = false },
                        )
                    }

                    SpecificationsCard(
                        design = design,
                        isEditing = isEditing,
                        editCustomFields = editCustomFields,
                        onEditCustomFieldsChange = { editCustomFields = it },
                    )

                    if (showSourceSelector) {
                        ImageSourceSelectorDialog(
                            onDismiss = { showSourceSelector = false },
                            onChooseGallery = {
                                showSourceSelector = false
                                imagePickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onTakePhoto = {
                                showSourceSelector = false
                                try {
                                    val uri = getTempUri(context)
                                    tempPhotoUriString = uri.toString()
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// #region Extracted dialogs

/**
 * Asks whether to pick an existing photo or take a new one.
 *
 * Each action is passed as a whole lambda rather than being reconstructed here, so the
 * caller keeps ownership of the exact statement order - dismissing before launching is
 * load-bearing, and splitting it into "dismiss, then act" would change behaviour.
 */
@Composable
private fun ImageSourceSelectorDialog(
    onDismiss: () -> Unit,
    onChooseGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Image Source", style = MaterialTheme.typography.titleLargeStrong) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)) {
                Button(
                    onClick = onChooseGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AtelierShapes.CornerMedium
                ) {
                    Text("Choose from Gallery")
                }

                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AtelierShapes.CornerMedium
                ) {
                    Text("Take Photo with Camera")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
// #endregion


/**
 * Three-piece attributes and custom fields, in both read and edit modes.
 *
 * Owns its own tab selection and new-field inputs; only the field map crosses the
 * boundary, hoisted so the parent still drives the save.
 */
@Composable
private fun SpecificationsCard(
    design: Design,
    isEditing: Boolean,
    editCustomFields: Map<String, String>,
    onEditCustomFieldsChange: (Map<String, String>) -> Unit,
) {
    // Specifications Card
    val specsToUse = if (isEditing) editCustomFields else design.customFields

    if (specsToUse.isNotEmpty() || isEditing) {
        Card(
            shape = AtelierShapes.CornerLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AtelierSpacing.Base),
                verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
            ) {
                Text("Specifications", style = MaterialTheme.typography.titleMediumStrong)

                val specTabs = listOf("Top (Kameez)", "Bottom (Salwar)", "Dupatta", "General")
                var selectedSpecTab by remember { mutableStateOf(0) }

                ScrollableTabRow(
                    selectedTabIndex = selectedSpecTab,
                    edgePadding = AtelierSpacing.None,
                    indicator = {},
                    divider = {}
                ) {
                    specTabs.forEachIndexed { index, title ->
                        val isSelected = selectedSpecTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedSpecTab = index },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    style = if (isSelected) MaterialTheme.typography.labelLargeStrong else MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .padding(horizontal = AtelierSpacing.Micro, vertical = 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                        )
                    }
                }

                val prefix = when (selectedSpecTab) {
                    0 -> "Top - "
                    1 -> "Bottom - "
                    2 -> "Dupatta - "
                    else -> ""
                }

                val filteredSpecs = specsToUse.filter { (key, _) ->
                    if (selectedSpecTab < 3) {
                        key.startsWith(prefix)
                    } else {
                        !key.startsWith("Top - ") && !key.startsWith("Bottom - ") && !key.startsWith("Dupatta - ")
                    }
                }

                if (filteredSpecs.isEmpty()) {
                    Text(
                        "No specifications under this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AtelierSpacing.Cozy)
                    )
                } else {
                    filteredSpecs.forEach { (key, value) ->
                        val cleanLabel = if (selectedSpecTab < 3) key.removePrefix(prefix) else key
                        if (isEditing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = AtelierSpacing.Micro),
                                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                            ) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { newValue ->
                                        if (newValue.length <= 200) {
                                            val updated = editCustomFields.toMutableMap()
                                            updated[key] = newValue
                                            onEditCustomFieldsChange(updated)
                                        }
                                    },
                                    label = { Text(cleanLabel) },
                                    modifier = Modifier.weight(1f),
                                    shape = AtelierShapes.CornerMedium
                                )
                                val defaultFields = setOf(
                                    "Top - Fabric", "Top - Work", "Top - Color",
                                    "Bottom - Fabric", "Bottom - Work", "Bottom - Color",
                                    "Dupatta - Fabric", "Dupatta - Work", "Dupatta - Color"
                                )
                                if (!defaultFields.contains(key)) {
                                    IconButton(
                                        onClick = {
                                            val updated = editCustomFields.toMutableMap()
                                            updated.remove(key)
                                            onEditCustomFieldsChange(updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove")
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AtelierSpacing.Micro),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cleanLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = value.ifBlank { "Not Specified" },
                                    style = MaterialTheme.typography.bodyMediumStrong
                                )
                            }
                        }
                    }
                }

                if (isEditing) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = AtelierSpacing.Compact))
                    
                    var newKey by remember { mutableStateOf("") }
                    var selectedScopeIndex by remember { mutableStateOf(0) }
                    val scopes = listOf("Top", "Bottom", "Dupatta", "General")

                    Text("Add New Custom Field", style = MaterialTheme.typography.bodyMediumStrong)

                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { if (it.length <= 50) newKey = it },
                        label = { Text("Field Name (e.g. Sleeve Style, Lining)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AtelierShapes.CornerMedium
                    )

                    Text("Apply Field To:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                    ) {
                        scopes.forEachIndexed { idx, scopeName ->
                            FilterChip(
                                selected = selectedScopeIndex == idx,
                                onClick = { selectedScopeIndex = idx },
                                label = { Text(scopeName) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                val scopedKey = when (selectedScopeIndex) {
                                    0 -> "Top - ${newKey.trim()}"
                                    1 -> "Bottom - ${newKey.trim()}"
                                    2 -> "Dupatta - ${newKey.trim()}"
                                    else -> newKey.trim()
                                }
                                if (!editCustomFields.containsKey(scopedKey)) {
                                    val updated = editCustomFields.toMutableMap()
                                    updated[scopedKey] = ""
                                    onEditCustomFieldsChange(updated)
                                }
                                newKey = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = AtelierShapes.CornerMedium,
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
        }
    }
}

/**
 * Edit mode for sizes, stock and per-size price.
 *
 * Self-contained: the size catalogue, the custom-size dialog, the pending removal and the
 * per-row stock/price text buffers are all local. Only the resulting size list crosses the
 * boundary, so the parent still owns what gets saved.
 */
@Composable
private fun SizeStockPriceEditorCard(
    editSizes: List<SizeInventory>,
    onEditSizesChange: (List<SizeInventory>) -> Unit,
) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Sizes, Stock & Prices", style = MaterialTheme.typography.titleMediumStrong)
            
            var showCustomSizeDialog by remember { mutableStateOf(false) }
            var newSizeInput by remember { mutableStateOf("") }
            var sizeToRemove by remember { mutableStateOf<String?>(null) }
            val availableSizes = remember { mutableStateListOf(*SizeUtils.sortSizes(listOf("S", "M", "L", "XL", "XXL")).toTypedArray()) }
            val sizeCache = remember { mutableMapOf<String, SizeInventory>() }
            
            LaunchedEffect(editSizes) {
                var addedAny = false
                editSizes.forEach {
                    if (!availableSizes.contains(it.size)) {
                        availableSizes.add(it.size)
                        addedAny = true
                    }
                }
                if (addedAny) {
                    val sorted = SizeUtils.sortSizes(availableSizes.toList())
                    availableSizes.clear()
                    availableSizes.addAll(sorted)
                }
            }

            val editSizesScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalFadingEdges(editSizesScrollState)
                    .horizontalScroll(editSizesScrollState),
                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableSizes.forEach { size ->
                    val isEnabled = editSizes.any { it.size == size }
                    FilterChip(
                        selected = isEnabled,
                        onClick = {
                            if (isEnabled) {
                                val inventory = editSizes.find { it.size == size }
                                if (inventory != null) {
                                    sizeCache[size] = inventory
                                }
                                if (inventory != null && (inventory.stock > 0 || inventory.price > 0L)) {
                                    sizeToRemove = size
                                } else {
                                    val updated = editSizes.toMutableList()
                                    updated.removeAll { it.size == size }
                                    onEditSizesChange(updated)
                                }
                            } else {
                                val cached = sizeCache[size]
                                val updated = editSizes.toMutableList()
                                updated.add(cached ?: SizeInventory(size, 0, 0L))
                                onEditSizesChange(updated)
                            }
                        },
                        label = { Text(size, style = MaterialTheme.typography.labelLargeStrong) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
                InputChip(
                    selected = false,
                    onClick = { showCustomSizeDialog = true },
                    label = { Text("+ Custom") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }

            if (editSizes.isEmpty()) {
                Text(
                    "Select sizes above to configure stock and pricing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AtelierSpacing.Cozy)
                )
            } else {
                editSizes.sortedBy { SizeUtils.getSizeSortWeight(it.size) }.forEach { inv ->
                    var stockText by remember(inv) { mutableStateOf(inv.stock.toString()) }
                    var priceText by remember { 
                        val initialText = if (inv.price == 0L) "" else (inv.price / 100.0).toString()
                        mutableStateOf(initialText) 
                    }

                    LaunchedEffect(inv.price) {
                        val parsedCents = FileUtils.parsePriceToCents(priceText)
                        if (parsedCents != inv.price) {
                            priceText = if (inv.price == 0L) "" else (inv.price / 100.0).toString()
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
                            Text(inv.size, style = MaterialTheme.typography.labelLargeStrong, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { newValue ->
                                val digits = newValue.filter { it.isDigit() }
                                if (digits.length <= 6) {
                                    stockText = digits
                                    val updated = editSizes.toMutableList()
                                    val idx = updated.indexOfFirst { it.size == inv.size }
                                    if (idx >= 0) {
                                        updated[idx] = inv.copy(stock = digits.toIntOrNull() ?: 0)
                                        onEditSizesChange(updated)
                                    }
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
                                if (dotCount <= 1 && sanitized.length <= 9) {
                                    val dotIndex = sanitized.indexOf('.')
                                    if (dotIndex == -1 || sanitized.length - dotIndex - 1 <= 2) {
                                        priceText = sanitized
                                        val parsedCents = FileUtils.parsePriceToCents(sanitized)
                                        val updated = editSizes.toMutableList()
                                        val idx = updated.indexOfFirst { it.size == inv.size }
                                        if (idx >= 0) {
                                            updated[idx] = inv.copy(price = parsedCents)
                                            onEditSizesChange(updated)
                                        }
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
            }

            if (showCustomSizeDialog) {
                val normInput = newSizeInput.trim().uppercase()
                val sizeExists = remember(newSizeInput, availableSizes) {
                    normInput.isNotEmpty() && availableSizes.contains(normInput)
                }
                AlertDialog(
                    onDismissRequest = { showCustomSizeDialog = false },
                    title = { Text("Add Custom Size", style = MaterialTheme.typography.titleLargeStrong) },
                    text = {
                        OutlinedTextField(
                            value = newSizeInput,
                            onValueChange = { if (it.length <= 10) newSizeInput = it },
                            label = { Text("Size Label (e.g. 3XL)") },
                            singleLine = true,
                            shape = AtelierShapes.CornerMedium,
                            isError = sizeExists,
                            supportingText = if (sizeExists) {
                                { Text("This size is already configured!", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newSizeInput.isNotBlank() && !sizeExists) {
                                    val norm = newSizeInput.trim().uppercase()
                                    if (!availableSizes.contains(norm)) {
                                        availableSizes.add(norm)
                                        val sorted = SizeUtils.sortSizes(availableSizes.toList())
                                        availableSizes.clear()
                                        availableSizes.addAll(sorted)
                                    }
                                    val updated = editSizes.toMutableList()
                                    if (!updated.any { it.size == norm }) {
                                        updated.add(SizeInventory(norm, 0, 0L))
                                        onEditSizesChange(updated)
                                    }
                                    newSizeInput = ""
                                    showCustomSizeDialog = false
                                }
                            },
                            enabled = newSizeInput.isNotBlank() && !sizeExists,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCustomSizeDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (sizeToRemove != null) {
                AlertDialog(
                    onDismissRequest = { sizeToRemove = null },
                    title = { Text("Remove Size $sizeToRemove?") },
                    text = { Text("This will clear the price and stock values configured for this size. Are you sure you want to remove it?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val inventory = editSizes.find { it.size == sizeToRemove }
                                if (inventory != null) {
                                    sizeCache[sizeToRemove!!] = inventory
                                }
                                val updated = editSizes.toMutableList()
                                updated.removeAll { it.size == sizeToRemove }
                                onEditSizesChange(updated)
                                sizeToRemove = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { sizeToRemove = null },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Read-only inventory and pricing, with the size selector.
 *
 * Reads nothing but the design. The selected-size index is local and keyed on design.id
 * rather than the design object, so a background sync refresh does not reset the choice.
 */
@Composable
private fun InventoryPricingCard(design: Design) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Inventory & Pricing", style = MaterialTheme.typography.titleMediumStrong)

            if (design.sizeInventories.isEmpty()) {
                Text(
                    "No size options configured for this design.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedInventories = design.sizeInventories.sortedBy { SizeUtils.getSizeSortWeight(it.size) }
                // Keyed on the design ID (not the object) so a background sync
                // refresh does not reset the user's selection; clamped as a
                // value instead of writing state during composition (BUG-16).
                var selectedSizeIndexRaw by remember(design.id) { mutableStateOf(0) }
                val selectedSizeIndex = selectedSizeIndexRaw.coerceIn(0, (sortedInventories.size - 1).coerceAtLeast(0))

                val activeInventory = sortedInventories.getOrNull(selectedSizeIndex)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = AtelierSpacing.Micro),
                    horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                ) {
                    sortedInventories.forEachIndexed { index, inv ->
                        val isSelected = selectedSizeIndex == index
                        InputChip(
                            selected = isSelected,
                            onClick = { selectedSizeIndexRaw = index },
                            label = { Text(inv.size, style = MaterialTheme.typography.labelLargeStrong) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(AtelierSpacing.Compact))

                if (activeInventory != null) {
                    val stockColor = when {
                        activeInventory.stock == 0 -> MaterialTheme.colorScheme.error
                        activeInventory.stock <= LOW_STOCK_THRESHOLD ->
                            MaterialTheme.colorScheme.warning
                        else -> MaterialTheme.colorScheme.success
                    }

                    Card(
                        shape = AtelierShapes.CornerMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AtelierSpacing.Base),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "₹${FileUtils.formatPrice(activeInventory.price)}",
                                    style = MaterialTheme.typography.titleMediumStrong,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Availability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (activeInventory.stock == 0) "Out of Stock" else "${activeInventory.stock} items",
                                    style = MaterialTheme.typography.titleMediumStrong,
                                    color = stockColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add and remove karigars for this design.
 *
 * Its working copy and the new-name input are local; only the confirmed list crosses
 * back out, so the parent still owns what gets saved.
 */
@Composable
private fun KarigarEditorDialog(
    design: Design,
    isEditing: Boolean,
    editKarigars: List<String>,
    viewModel: DesignDetailViewModel,
    onEditKarigarsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
val karigarsListToUse = if (isEditing) editKarigars else design.karigars
var tempKarigars by remember(karigarsListToUse) { mutableStateOf(karigarsListToUse) }
var newKarigarInput by remember { mutableStateOf("") }

AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage Karigars", style = MaterialTheme.typography.titleLargeStrong) },
    text = {
        Column(
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = newKarigarInput,
                onValueChange = { if (it.length <= 50) newKarigarInput = it },
                label = { Text("Karigar Name") },
                singleLine = true,
                shape = AtelierShapes.CornerMedium,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val trimmed = newKarigarInput.trim()
                            if (trimmed.isNotBlank() && !tempKarigars.contains(trimmed)) {
                                tempKarigars = tempKarigars + trimmed
                                newKarigarInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Karigar")
                    }
                }
            )

            if (tempKarigars.isEmpty()) {
                Text(
                    "No Karigars in list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = AtelierSpacing.Micro),
                    horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                ) {
                    tempKarigars.forEach { karigar ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(karigar) },
                            trailingIcon = {
                                // 48dp tap target on the Box; glyph stays 18dp.
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Remove"
                                        ) { tempKarigars = tempKarigars - karigar },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Remove $karigar",
                                        modifier = Modifier.size(RemoveIconSize)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    },
    confirmButton = {
        Button(
            onClick = {
                if (isEditing) {
                    onEditKarigarsChange(tempKarigars)
                } else {
                    viewModel.updateKarigars(tempKarigars)
                }
                onDismiss()
            }
        ) {
            Text("Save Changes")
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
)
}

/**
 * Shows the karigars assigned to this design, with an edit affordance.
 */
@Composable
private fun AssignedKarigarsCard(
    design: Design,
    isEditing: Boolean,
    editKarigars: List<String>,
    onManageKarigars: () -> Unit,
) {
// Assigned Karigars Card
Card(
    shape = AtelierShapes.CornerLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AtelierSpacing.Base),
        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Assigned Karigars", style = MaterialTheme.typography.titleMediumStrong)
            IconButton(onClick = onManageKarigars) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Karigars",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        val karigarsListToUse = if (isEditing) editKarigars else design.karigars

        if (karigarsListToUse.isEmpty()) {
            Text(
                "No Karigars assigned to this design.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = AtelierSpacing.Micro),
                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                verticalAlignment = Alignment.CenterVertically
            ) {
                karigarsListToUse.forEach { karigar ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(karigar, style = MaterialTheme.typography.labelLarge) },
                        shape = AtelierShapes.CornerSmall
                    )
                }
            }
        }
    }
}
}

/**
 * Design name and category - an editable form in edit mode, a heading otherwise.
 */
@Composable
private fun TitleCategoryCard(
    design: Design,
    isEditing: Boolean,
    editName: String,
    editCategory: String,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
) {
// Product Title & Category Card
if (isEditing) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Basic Details", style = MaterialTheme.typography.titleMediumStrong)
            
            OutlinedTextField(
                value = editName,
                onValueChange = { if (it.length <= 80) onNameChange(it) },
                label = { Text("Design Name / Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AtelierShapes.CornerMedium
            )

            OutlinedTextField(
                value = editCategory,
                onValueChange = { if (it.length <= 80) onCategoryChange(it) },
                label = { Text("Category (e.g. Salwar Kameez)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AtelierShapes.CornerMedium
            )
        }
    }
} else {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
        ) {
            Text(
                text = design.name,
                style = MaterialTheme.typography.headlineSmallStrong,
                color = MaterialTheme.colorScheme.primary
            )
            SuggestionChip(
                onClick = {},
                label = { Text(design.category) },
                shape = AtelierShapes.CornerSmall
            )
        }
    }
}
}

/**
 * Hero photo. Tapping it in edit mode asks for a new one.
 *
 * While editing, a freshly picked LOCAL photo deliberately outranks the uploaded one -
 * otherwise replacing a photo would still show the old image until sync caught up.
 */
@Composable
private fun DesignImageCard(
    design: Design,
    isEditing: Boolean,
    editImagePath: String?,
    onPickImage: () -> Unit,
) {
// Design Image Card
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(HeroImageHeight)
        .clip(AtelierShapes.CornerLarge)
        .background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            )
        )
        .clickable(enabled = isEditing) { onPickImage() },
    contentAlignment = Alignment.Center
) {
    val imagePathToUse = if (isEditing) editImagePath else design.imagePath
    // While editing, a freshly picked local photo must win over the
    // uploaded one - otherwise the user sees the old image after replacing it.
    val detailImageModel = rememberDesignImageModel(
        imagePath = imagePathToUse,
        imageRef = if (isEditing && editImagePath != null) null else design.imageRef
    )
    if (detailImageModel != null) {
        Image(
            painter = rememberAsyncImagePainter(detailImageModel),
            contentDescription = if (isEditing) "Edit Photo" else design.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AtelierSpacing.Cozy)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(AtelierSpacing.Compact)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Change Photo",
                    tint = Color.White,
                    modifier = Modifier.size(OverlayIconSize)
                )
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = "Upload Photo",
                modifier = Modifier.size(EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AtelierSpacing.Compact))
            Text("Upload Design Photo", style = MaterialTheme.typography.labelLarge)
        }
    }
}
}

/**
 * Shown when the design changed underneath an in-progress edit.
 *
 * The overwrite action is passed in whole rather than reconstructed here, so the caller
 * keeps ownership of what "overwrite" means and in what order it happens.
 */
@Composable
private fun SaveConflictDialog(
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Design Changed During Edit") },
        text = { Text("This design was updated by a sync while you were editing. Save anyway and overwrite those changes?") },
        confirmButton = {
            TextButton(
                onClick = onOverwrite,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Overwrite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Delete confirmation.
 *
 * The confirm action is ONE lambda supplied by the caller. Splitting it into "confirm" plus
 * an automatic dismiss would reorder the statements, and the order matters: deleteDesign()
 * flips uiState to NotFound on the next emission.
 */
@Composable
private fun DeleteDesignDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Design") },
        text = { Text("Are you sure you want to delete this design? This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * The chrome shared by the Loading and Not-Found states: a titled bar with a back action
 * and a centred body. Both states rendered identical scaffolds; this removes the duplicate.
 *
 * Carries its own @OptIn because TopAppBar is experimental and a top-level composable does
 * NOT inherit the file-level opt-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailStatusScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLargeStrong) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}