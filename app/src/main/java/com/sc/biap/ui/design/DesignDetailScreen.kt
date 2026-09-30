package com.sc.biap.ui.design

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.DesignPhoto
import com.sc.biap.data.SizeInventory
import com.sc.biap.data.stats
import com.sc.biap.ui.DesignImageState
import com.sc.biap.ui.rememberDesignImage
import com.sc.biap.security.AppLock
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
/** The rupee column keeps one edge while typing, so the prices still read down. */
private val PriceWellWidth = 110.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignDetailScreen(
    designId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Open with this size already selected, set when a size cell in the catalogue was tapped.
     *
     * The inventory card already picks one size at a time, so a deep link is simply a
     * different starting selection - no scrolling, no highlight overlay, no second way of
     * showing the same thing.
     */
    focusSize: String? = null,
    viewModel: DesignDetailViewModel = defaultDesignDetailViewModel(designId)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Edit states
    var editName by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }
    // The whole set of photographs being edited, cover first. Was a single path; a design
    // may now hold up to Design.MAX_PHOTOS.
    var editPhotos by remember { mutableStateOf<List<DesignPhoto>>(emptyList()) }
    var photoPage by remember { mutableIntStateOf(0) }
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

    // The size whose batch sheet is open, and the number to pre-fill in it. The number is
    // fetched when the sheet opens rather than held for every size, because it is a database
    // read and only one sheet is ever open.
    var sheetSize by remember { mutableStateOf<SizeInventory?>(null) }
    var suggestedBatchNumber by remember { mutableStateOf(1) }
    var confirmingResetAll by remember { mutableStateOf(false) }

    val batchesBySize by viewModel.batchesBySize.collectAsStateWithLifecycle()
    val hubKarigars by viewModel.hubKarigars.collectAsStateWithLifecycle()
    val detailMessage by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(sheetSize?.size) {
        sheetSize?.let { suggestedBatchNumber = viewModel.suggestBatchNumber(it.size) }
    }

    // Automatic assignment from the Business Hub.
    //
    // When the work written on a design names something a karigar is assigned to - "Block
    // Printing" against Printing - that karigar goes onto the design without being asked
    // for. Only while editing, because it writes into the edit buffer rather than saving.
    //
    // ADDITIVE ONLY, and the asymmetry is deliberate. Changing a work value never removes
    // anybody: somebody put there by hand is a deliberate act, and "this is no longer block
    // printing" is not a statement that the person who was doing it has stopped. Taking
    // someone off stays a manual decision, which is what the chips in the dialog are for.
    LaunchedEffect(editCustomFields, hubKarigars, isEditing) {
        if (!isEditing) return@LaunchedEffect
        val implied = viewModel.impliedKarigars(editCustomFields, editKarigars)
        if (implied.isNotEmpty()) editKarigars = editKarigars + implied
    }

    // Anything the batch or reset paths needed to say. Shown as a dialog rather than a
    // snackbar because this screen has no scaffold to host one, and every message here is
    // a refusal the owner has to act on rather than a passing note.
    detailMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Could not do that") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
            },
        )
    }

    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        AppLock.endTrustedFlow()
        if (uri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, uri)
                if (localPath != null && editPhotos.size < Design.MAX_PHOTOS) {
                    editPhotos = editPhotos + DesignPhoto(localPath = localPath)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        AppLock.endTrustedFlow()
        if (success && tempPhotoUri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, tempPhotoUri!!)
                if (localPath != null && editPhotos.size < Design.MAX_PHOTOS) {
                    editPhotos = editPhotos + DesignPhoto(localPath = localPath)
                }
            }
        }
    }

    sheetSize?.let { inventory ->
        // Re-read from the design rather than using the captured copy, so stock and price in
        // the sheet follow a batch being added instead of freezing at the moment it opened.
        val live = (state as? DesignDetailUiState.Success)
            ?.design?.sizeInventories?.firstOrNull { it.size == inventory.size }
            ?: inventory
        SizeBatchSheet(
            designName = (state as? DesignDetailUiState.Success)?.design?.name.orEmpty(),
            inventory = live,
            batches = batchesBySize[live.size].orEmpty(),
            suggestedNumber = suggestedBatchNumber,
            onAddBatch = { number, quantity, price, note ->
                viewModel.addBatch(live.size, number, quantity, price, note)
            },
            onResetSize = { viewModel.resetSize(live.size) },
            onEditSizes = { sheetSize = null; isEditing = true },
            onDismiss = { sheetSize = null },
        )
    }

    if (confirmingResetAll) {
        val design = (state as? DesignDetailUiState.Success)?.design
        ResetConfirmation(
            title = "Reset all stock to 0?",
            body = "Every size of " + (design?.name ?: "this design") + " goes to 0. Prices, " +
                "karigars, photographs and every other detail stay exactly as they are, and " +
                "the batch history is kept - each batch just shows nothing left.",
            onConfirm = { viewModel.resetWholeDesign(); confirmingResetAll = false },
            onDismiss = { confirmingResetAll = false },
        )
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

    // Back exits EDIT MODE first, exactly as the discard button beside it does - leaving
    // the screen mid-edit would drop the changes with nothing said.
    //
    // enabled = isEditing on purpose: outside edit mode this handler is transparent, so the
    // predictive back gesture keeps working and the catalogue peeks through under the
    // finger. Intercepting unconditionally is what turns predictive back back into a plain
    // button, which is the whole thing the platform is trying to stop apps doing.
    BackHandler(enabled = isEditing) { isEditing = false }

    when (val uiState = state) {
        DesignDetailUiState.Loading -> {
            DetailStatusScaffold(title = "Loading...", onBack = onBack, modifier = modifier) {
                LoadingIndicator()
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
                    editPhotos = design.photos
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
                            sizeInventories = editSizes,
                            customFields = editCustomFields.filterKeys { it.isNotBlank() },
                            karigars = editKarigars
                        ).withPhotos(editPhotos)
                        viewModel.updateDesign(updated)
                        isEditing = false
                    }
                )
            }

            Scaffold(
                topBar = {
                    if (isEditing) {
                        // The mode change is LOUD on purpose. Editing happens in place on the
                        // same screen, so without a signal you can be one careless tap from
                        // changing a record while believing you are reading it. The bar fills
                        // with primaryContainer, back becomes discard, and Save is the only
                        // saturated thing on the screen.
                        EditingTopBar(
                            canSave = editName.isNotBlank(),
                            onDiscard = { isEditing = false },
                            onSave = {
                                if (editName.isNotBlank()) {
                                    if (design.updatedAt != editBaseUpdatedAt) {
                                        showConflictDialog = true
                                    } else {
                                        val updated = design.copy(
                                            name = editName.trim(),
                                            category = editCategory.trim(),
                                            sizeInventories = editSizes,
                                            customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                            karigars = editKarigars
                                            // withPhotos carries each photo's own ref, so a
                                            // newly picked one arrives with a null ref and is
                                            // marked for upload while the previous object
                                            // stays intact until the new bytes land.
                                        ).withPhotos(editPhotos)
                                        viewModel.updateDesign(updated)
                                        isEditing = false
                                    }
                                }
                            },
                        )
                    } else {
                        ReadingTopBar(
                            archived = design.isArchived,
                            onBack = onBack,
                            onEdit = { isEditing = true },
                            onArchive = { viewModel.setArchived(true) },
                            onRestore = { viewModel.setArchived(false) },
                            onDelete = { showDeleteDialog = true },
                            onResetStock = { confirmingResetAll = true },
                        )
                    }
                },
                // The page drops a surface step while editing, so even the background says
                // which mode you are in.
                containerColor = if (isEditing) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else if (design.isArchived) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
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
                        editPhotos = editPhotos,
                        page = photoPage,
                        onPageChange = { photoPage = it },
                        onPickImage = { showSourceSelector = true },
                        onRemovePhoto = { index ->
                            editPhotos = editPhotos.filterIndexed { i, _ -> i != index }
                            photoPage = photoPage.coerceAtMost(maxOf(0, editPhotos.size - 2))
                        },
                    )

                    TitleCategoryCard(
                        design = design,
                        isEditing = isEditing,
                        editName = editName,
                        editCategory = editCategory,
                        onNameChange = { editName = it },
                        onCategoryChange = { editCategory = it },
                    )

                    // Inventory & Pricing
                    if (isEditing) {
                        SizeStockPriceEditorCard(
                            editSizes = editSizes,
                            onEditSizesChange = { editSizes = it },
                        )
                    } else {
                        if (design.isArchived) {
                            ArchivedBanner()
                        }
                        // The WHOLE matrix, every size, always. This screen used to show one
                        // size at a time behind chips, which made it thinner than the
                        // catalogue row it was opened from.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeading("Inventory & pricing")
                            MatrixHeader(design)
                            SizeMatrix(
                                design = design,
                                // Tapping a size opens its batch history, where adding a
                                // batch and resetting that size both live. Editing is one
                                // button inside the sheet, so the tap gained a destination
                                // rather than losing the one it had.
                                onSizeClick = { inventory ->
                                    if (!design.isArchived) sheetSize = inventory
                                },
                            )
                            UnpricedBanner(
                                sizes = design.sizeInventories
                                    .filter { it.price <= 0L }
                                    .sortedBy { SizeUtils.getSizeSortWeight(it.size) }
                                    .map { it.size },
                            )
                        }
                    }

                    if (isEditing) {
                        AssignedKarigarsCard(
                            design = design,
                            isEditing = true,
                            editKarigars = editKarigars,
                            onManageKarigars = { showEditKarigarsDialog = true },
                        )
                    } else if (design.karigars.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionHeading("Karigars", modifier = Modifier.weight(1f))
                                if (!design.isArchived) {
                                    Text(
                                        "Edit",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { isEditing = true },
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                design.karigars.forEach { name ->
                                    KarigarChip(name = name, editing = false, onRemove = {})
                                }
                            }
                        }
                    }

                    if (showEditKarigarsDialog) {
                        KarigarEditorDialog(
                            design = design,
                            isEditing = isEditing,
                            editKarigars = editKarigars,
                            hubKarigars = hubKarigars,
                            onAddToHub = { viewModel.addKarigarToHub(it) },
                            viewModel = viewModel,
                            onEditKarigarsChange = { editKarigars = it },
                            onDismiss = { showEditKarigarsDialog = false },
                        )
                    }

                    if (isEditing) {
                        SpecificationsCard(
                            design = design,
                            isEditing = true,
                            editCustomFields = editCustomFields,
                            onEditCustomFieldsChange = { editCustomFields = it },
                        )
                    } else {
                        val groups = remember(design.customFields) {
                            groupAttributes(design.customFields)
                        }
                        if (groups.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionHeading("Attributes")
                                groups.forEach { (title, rows) ->
                                    AttributeGroup(title = title, rows = rows, editing = false)
                                }
                            }
                        }
                    }

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                    shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium)) {
                    Text("Choose from Gallery")
                }

                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(shape = AtelierShapes.CornerMedium)) {
                    Text("Take Photo with Camera")
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SizeStockPriceEditorCard(
    editSizes: List<SizeInventory>,
    onEditSizesChange: (List<SizeInventory>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
        Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
            SectionHeading("Sizes, stock & prices")

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
                    TonalPill(
                        text = size,
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
                        leading = if (isEnabled) {
                            {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                }
                TonalPill(
                    text = "Custom",
                    selected = false,
                    onClick = { showCustomSizeDialog = true },
                    leading = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
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
                        // The size keeps the catalogue's own cell, coded exactly as it is
                        // everywhere else. Editing the numbers must not change what the size
                        // looks like, or the shape stops being a reliable signal.
                        DetailSizeCell(inv)

                        WellTextField(
                            label = "Stock",
                            value = stockText,
                            placeholder = "0",
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )

                        WellTextField(
                            label = "Price",
                            value = priceText,
                            // Never "0". An unpriced size is a size nobody has decided on,
                            // and a zero in the field would read as a decision.
                            placeholder = "Set",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            // Fixed rather than weighted, so the rupee column stays put while
                            // the stock beside it grows a digit.
                            modifier = Modifier.width(PriceWellWidth),
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
                        Button(shapes = ButtonDefaults.shapes(), onClick = {
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
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showCustomSizeDialog = false },
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
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = {
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
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { sizeToRemove = null },
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
/**
 * Says plainly that this design is out of the working catalogue, and why that is reversible.
 *
 * Tonal rather than an error colour: archiving is a normal, intended act, not a fault.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArchivedNotice() {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AtelierSpacing.Base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
        ) {
            Icon(
                Icons.Default.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text("Archived", style = MaterialTheme.typography.titleMediumStrong)
                Text(
                    "Hidden from the catalogue. Its stock and prices are kept, and it can be " +
                        "restored at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InventoryPricingCard(design: Design, focusSize: String? = null) {
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
                var selectedSizeIndexRaw by remember(design.id) {
                    // Start on the size the catalogue was asking about, if it was asking.
                    // coerceAtLeast(0) covers a size that has since been renamed or removed -
                    // arriving at the first size is better than arriving at nothing.
                    mutableStateOf(
                        sortedInventories
                            .indexOfFirst { it.size.equals(focusSize, ignoreCase = true) }
                            .coerceAtLeast(0)
                    )
                }
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
/**
 * Pick this design's karigars from the Business Hub list.
 *
 * WHY THIS IS A PICKER AND NOT A TEXT FIELD ANY MORE:
 *   Typed names cannot be matched across designs. "Deen Mohd." on one and "Deen Mohd" on
 *   another are two people to the app and one to the workshop, which is exactly what breaks
 *   assigning work by activity. The Hub is the master list, so the ordinary path is tapping
 *   a name that already exists.
 *
 *   Adding someone new is still here, because the alternative is being unable to record a
 *   karigar until you have visited another screen. A name added here joins the Hub, so the
 *   two lists never diverge.
 *
 * The design keeps NAMES rather than ids, deliberately: a design is a historical record and
 * should still read correctly if somebody is later removed from the Hub.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KarigarEditorDialog(
    design: Design,
    isEditing: Boolean,
    editKarigars: List<String>,
    hubKarigars: List<com.sc.biap.data.Karigar>,
    onAddToHub: (String) -> Unit,
    viewModel: DesignDetailViewModel,
    onEditKarigarsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val karigarsListToUse = if (isEditing) editKarigars else design.karigars
    var tempKarigars by remember(karigarsListToUse) { mutableStateOf(karigarsListToUse) }
    var newKarigarInput by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    fun toggle(name: String) {
        tempKarigars = if (tempKarigars.any { it.equals(name, ignoreCase = true) }) {
            tempKarigars.filterNot { it.equals(name, ignoreCase = true) }
        } else {
            tempKarigars + name
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign karigars", style = MaterialTheme.typography.titleLargeStrong) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hubKarigars.isEmpty()) {
                    Text(
                        "Nobody on the Hub list yet. Add someone below, or open Business Hub " +
                            "from the account menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Tap a name to put them on this design.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        hubKarigars.forEach { karigar ->
                            val on = tempKarigars.any { it.equals(karigar.name, ignoreCase = true) }
                            FilterChip(
                                selected = on,
                                onClick = { toggle(karigar.name) },
                                label = { Text(karigar.name) },
                                leadingIcon = if (on) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }

                // Anyone on this design who is no longer on the Hub - removed since, or
                // typed in before the Hub existed. Shown so they can still be taken off,
                // rather than being stuck on the design with no control for them.
                val offHub = tempKarigars.filterNot { name ->
                    hubKarigars.any { it.name.equals(name, ignoreCase = true) }
                }
                if (offHub.isNotEmpty()) {
                    Text(
                        "Not on the Hub list",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                    ) {
                        offHub.forEach { name ->
                            FilterChip(
                                selected = true,
                                onClick = { toggle(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newKarigarInput,
                    onValueChange = { if (it.length <= 50) newKarigarInput = it },
                    label = { Text("Add someone new") },
                    singleLine = true,
                    shape = AtelierShapes.CornerMedium,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val trimmed = newKarigarInput.trim()
                                if (trimmed.isNotBlank() &&
                                    tempKarigars.none { it.equals(trimmed, ignoreCase = true) }
                                ) {
                                    tempKarigars = tempKarigars + trimmed
                                    // Joins the Hub too, so the master list stays master.
                                    onAddToHub(trimmed)
                                    newKarigarInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add karigar")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isEditing) onEditKarigarsChange(tempKarigars)
                else viewModel.updateKarigars(tempKarigars)
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shows the karigars assigned to this design, with an edit affordance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    // Wells, not outlined fields - the same ones the capture form uses, because this is
    // the same job. A stroke here would also collide with the dashed edge that now means
    // "empty" everywhere in the app.
    Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
        WellTextField(
            label = "Design name / code",
            value = editName,
            onValueChange = { if (it.length <= 80) onNameChange(it) },
            placeholder = "e.g. Green Panel - Style 2",
        )
        WellTextField(
            label = "Category",
            value = editCategory,
            onValueChange = { if (it.length <= 80) onCategoryChange(it) },
            placeholder = "e.g. Salwar Kameez",
        )
    }
} else {
    // No card around the name. It is the title of the page, not an item on it - boxing it
    // made it read as one more row rather than the thing every row belongs to.
    val stats = design.stats()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = design.name,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.8).sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (design.category.isNotBlank()) OutlinePill(design.category)
            // At most ONE state pill, and the unpriced one wins. A size nobody can quote is
            // a worse problem than a size running low, and it is the one the catalogue row
            // has no room to say.
            when {
                stats.unpricedSizes > 0 -> StatePill(
                    "${stats.unpricedSizes} size${if (stats.unpricedSizes == 1) "" else "s"} unpriced"
                )
                stats.outSizes > 0 -> StatePill(
                    "${stats.outSizes} size${if (stats.outSizes == 1) "" else "s"} out"
                )
            }
        }
    }
}
}

/**
 * The photographs, swipeable, with a strip underneath while editing.
 *
 * WHAT WAS WRONG BEFORE: a single frame at a fixed 280dp with ContentScale.Crop. These are
 * full-length shots of a garment on a model, so cropping to a squat landscape box took the
 * head and the hem - the two ends of the thing the screen exists to show. It now fits the
 * whole photo into a tall frame and letterboxes onto the panel instead.
 *
 * While editing, a freshly picked LOCAL photo outranks the uploaded one automatically: it
 * arrives as a DesignPhoto carrying a local path and no ref, so there is no longer a
 * special case for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DesignImageCard(
    design: Design,
    isEditing: Boolean,
    editPhotos: List<DesignPhoto>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPickImage: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
) {
    val photos = if (isEditing) editPhotos else design.photos

    Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
        if (photos.isEmpty()) {
            // A record, not a gap. The detail screen says the design has no photograph and
            // offers to fix it; the capture form's panel is a different message for a
            // different moment and does not belong here.
            NoPhotoBlock(
                canAdd = isEditing,
                onAdd = onPickImage,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            DesignPhotoPager(
                photos = photos,
                contentDescription = design.name,
                page = page,
                onPageChange = onPageChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The strip appears only while editing. Outside edit mode the pager and its dots
        // already say how many photographs there are, and a row of tiles carrying remove
        // badges you cannot use would be furniture.
        if (isEditing) {
            DesignPhotoStrip(
                photos = photos,
                editable = true,
                onAdd = onPickImage,
                onRemove = onRemovePhoto,
                selected = page,
                onSelect = onPageChange,
            )
        }
    }
}

/**
 * Shown when a design HAS a photo that this device could not fetch.
 *
 * Deliberately worded as a temporary problem rather than a missing one: the bytes are in
 * Storage, so telling someone their photo is gone would send them off to re-shoot a design
 * that never needed it. The retry is here because the earlier behaviour - resolve once, then
 * stay blank forever - turned a moment without signal into a permanently empty frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoUnavailable(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(EmptyStateIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Photo could not be loaded",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "It is saved in the cloud - check your connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetry,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(AtelierSpacing.Micro))
            Text("Try again")
        }
    }
}

/**
 * Shown when the design changed underneath an in-progress edit.
 *
 * The overwrite action is passed in whole rather than reconstructed here, so the caller
 * keeps ownership of what "overwrite" means and in what order it happens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onOverwrite,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Overwrite")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteDesignDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete permanently?") },
        text = {
            Text(
                "This destroys the design and its whole size, price and karigar history on " +
                    "every device. It cannot be undone. If you only want it out of the " +
                    "catalogue, close this and leave it archived instead."
            )
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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