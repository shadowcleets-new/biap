package com.sc.biap.ui.design

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.data.SizeInventory
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.horizontalFadingEdges
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.titleLargeStrong
import com.sc.biap.theme.titleMediumStrong
import com.sc.biap.util.FileUtils
import java.io.File

@Composable
fun defaultAddDesignViewModel(): AddDesignViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { AddDesignViewModel(DefaultDataRepository.getInstance(context)) }
}
// Artwork and control geometry: intentionally not on the AtelierSpacing scale, which
// governs layout rhythm rather than the intrinsic size of images, icons and chips.
private val ImagePickerHeight = 200.dp
private val OverlayIconSize = 20.dp
private val EmptyStateIconSize = 40.dp
private val SizeTokenWidth = 56.dp
private val RemoveIconSize = 18.dp


@OptIn(ExperimentalMaterial3Api::class)
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
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempPhotoUri = remember(tempPhotoUriString) {
        tempPhotoUriString?.let { Uri.parse(it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
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
        if (success && tempPhotoUri != null) {
            scope.launch {
                val localPath = FileUtils.copyUriToInternalStorage(context, tempPhotoUri)
                viewModel.onImageSelected(localPath)
            }
        }
    }

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val isFormDirty = remember(state) {
        state.name.isNotEmpty() ||
        state.category != AddDesignViewModel.DEFAULT_CATEGORY ||
        state.imagePath != null ||
        state.sizes.isNotEmpty() ||
        state.karigars.isNotEmpty() ||
        state.customFields.values.any { it.isNotEmpty() }
    }

    BackHandler {
        if (isFormDirty) {
            showUnsavedChangesDialog = true
        } else {
            onBack()
        }
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Discard Design?") },
            text = { Text("You have unsaved changes that will be lost. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            // Consume BEFORE navigating: the flag must not outlive the navigation it caused.
            viewModel.onSaveNavigationHandled()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Design", style = MaterialTheme.typography.titleLargeStrong) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isFormDirty) {
                            showUnsavedChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveDesign() },
                        enabled = state.name.isNotBlank(),
                        shape = AtelierShapes.CornerMedium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Save", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(AtelierSpacing.Micro))
                        Text("Save")
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
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Large)
        ) {
            // Image Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ImagePickerHeight)
                    .clip(AtelierShapes.CornerLarge)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .clickable { showSourceSelector = true },
                contentAlignment = Alignment.Center
            ) {
                if (state.imagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(FileUtils.resolveImageFile(context, state.imagePath)),
                        contentDescription = "Design Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
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

            // Design Details
            Card(
                shape = AtelierShapes.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(AtelierSpacing.Base),
                    verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
                ) {
                    Text("Basic Details", style = MaterialTheme.typography.titleMediumStrong)

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { if (it.length <= 80) viewModel.onNameChange(it) },
                        label = { Text("Design Name / Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = AtelierShapes.CornerMedium
                    )

                    OutlinedTextField(
                        value = state.category,
                        onValueChange = { if (it.length <= 80) viewModel.onCategoryChange(it) },
                        label = { Text("Category (e.g. Salwar Kameez)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = AtelierShapes.CornerMedium
                    )
                }
            }

            // Size Stock and Pricing Matrix
            Card(
                shape = AtelierShapes.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

                    var showCustomSizeDialog by remember { mutableStateOf(false) }
                    var newSizeInput by remember { mutableStateOf("") }
                    var sizeToRemove by remember { mutableStateOf<String?>(null) }

                    Text("Configure Size Scale", style = MaterialTheme.typography.bodyMediumStrong)
                    
                    val sizeScaleScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalFadingEdges(sizeScaleScrollState)
                            .horizontalScroll(sizeScaleScrollState),
                        horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.availableSizes.forEach { size ->
                            val isEnabled = state.sizes.any { it.size == size }
                            FilterChip(
                                selected = isEnabled,
                                onClick = {
                                    if (isEnabled) {
                                        val inventory = state.sizes.find { it.size == size }
                                        if (inventory != null && (inventory.stock > 0 || inventory.price > 0L)) {
                                            sizeToRemove = size
                                        } else {
                                            viewModel.updateSizeInventory(size, 0, 0L, false)
                                        }
                                    } else {
                                        viewModel.updateSizeInventory(size, 0, 0L, true, restoreFromCache = true)
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

                    if (sizeToRemove != null) {
                        AlertDialog(
                            onDismissRequest = { sizeToRemove = null },
                            title = { Text("Remove Size $sizeToRemove?") },
                            text = { Text("This will clear the price and stock values configured for this size. Are you sure you want to remove it?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        sizeToRemove?.let { viewModel.updateSizeInventory(it, 0, 0L, false) }
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

                    val enabledSizes = state.availableSizes.filter { size -> state.sizes.any { it.size == size } }
                    
                    if (enabledSizes.isEmpty()) {
                        Text(
                            "Select sizes above to configure stock and unique pricing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AtelierSpacing.Cozy)
                        )
                    } else {
                        enabledSizes.forEach { size ->
                            val inventory = state.sizes.find { it.size == size }
                            var stockText by remember(inventory) { mutableStateOf(inventory?.stock?.toString() ?: "") }
                            
                            // Safe price text field with decimal buffer and cents storage
                            var priceText by remember { 
                                val initialText = if (inventory == null || inventory.price == 0L) "" else (inventory.price / 100.0).toString()
                                mutableStateOf(initialText) 
                            }
                            
                            LaunchedEffect(inventory?.price) {
                                val currentPrice = inventory?.price ?: 0L
                                val parsedDouble = priceText.toDoubleOrNull() ?: 0.0
                                val parsedCents = (parsedDouble * 100).toLong()
                                if (parsedCents != currentPrice) {
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
                                    Text(size, style = MaterialTheme.typography.labelLargeStrong, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }

                                OutlinedTextField(
                                    value = stockText,
                                    onValueChange = { newValue ->
                                        val digitsOnly = newValue.filter { it.isDigit() }
                                        if (digitsOnly.length <= 6) {
                                            stockText = digitsOnly
                                            val parsedCents = FileUtils.parsePriceToCents(priceText)
                                            viewModel.updateSizeInventory(
                                                size,
                                                digitsOnly.toIntOrNull() ?: 0,
                                                parsedCents,
                                                true
                                            )
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
                                                viewModel.updateSizeInventory(
                                                    size,
                                                    stockText.toIntOrNull() ?: 0,
                                                    parsedCents,
                                                    true
                                                )
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
                        val sizeExists = remember(newSizeInput, state.availableSizes) {
                            normInput.isNotEmpty() && state.availableSizes.contains(normInput)
                        }
                        AlertDialog(
                            onDismissRequest = { showCustomSizeDialog = false },
                            title = { Text("Add Custom Size", style = MaterialTheme.typography.titleLargeStrong) },
                            text = {
                                OutlinedTextField(
                                    value = newSizeInput,
                                    onValueChange = { if (it.length <= 10) newSizeInput = it },
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
                                Button(
                                    onClick = {
                                        if (newSizeInput.isNotBlank() && !sizeExists) {
                                            val normalizedSize = newSizeInput.trim().uppercase()
                                            viewModel.addAvailableSize(normalizedSize)
                                            viewModel.updateSizeInventory(normalizedSize, 0, 0L, true, restoreFromCache = true)
                                            newSizeInput = ""
                                            showCustomSizeDialog = false
                                        }
                                    },
                                    enabled = newSizeInput.isNotBlank() && !sizeExists
                                ) {
                                    Text("Add")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomSizeDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            // Assigned Karigars
            Card(
                shape = AtelierShapes.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

                    var newKarigarName by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = newKarigarName,
                        onValueChange = { if (it.length <= 50) newKarigarName = it },
                        label = { Text("Karigar Name (e.g. Master Salim)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = AtelierShapes.CornerMedium,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newKarigarName.isNotBlank()) {
                                    viewModel.addKarigar(newKarigarName.trim())
                                    newKarigarName = ""
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (newKarigarName.isNotBlank()) {
                                        viewModel.addKarigar(newKarigarName.trim())
                                        newKarigarName = ""
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Karigar")
                            }
                        }
                    )

                    if (state.karigars.isEmpty()) {
                        Text(
                            "No Karigars assigned yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AtelierSpacing.Micro)
                        )
                    } else {
                        val karigarsScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalFadingEdges(karigarsScrollState)
                                .horizontalScroll(karigarsScrollState)
                                .padding(vertical = AtelierSpacing.Micro),
                            horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.karigars.forEach { karigar ->
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
                                                ) { viewModel.removeKarigar(karigar) },
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

            // Custom Attributes & Global Fields
            // Custom Attributes & Global Fields
            Card(
                shape = AtelierShapes.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(AtelierSpacing.Base),
                    verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
                ) {
                    Text("3-Piece Attributes & Custom Fields", style = MaterialTheme.typography.titleMediumStrong)

                    val attributeTabs = listOf("Top (Kameez)", "Bottom (Salwar)", "Dupatta", "General")
                    var selectedTab by remember { mutableStateOf(0) }

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = AtelierSpacing.None,
                        indicator = {},
                        divider = {}
                    ) {
                        attributeTabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = index },
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

                    val prefix = when (selectedTab) {
                        0 -> "Top - "
                        1 -> "Bottom - "
                        2 -> "Dupatta - "
                        else -> ""
                    }

                    val filteredFields = state.customFields.filter { (key, _) ->
                        if (selectedTab < 3) {
                            key.startsWith(prefix)
                        } else {
                            !key.startsWith("Top - ") && !key.startsWith("Bottom - ") && !key.startsWith("Dupatta - ")
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
                            val isGlobal = state.globalCustomFields.contains(key)
                            val cleanLabel = if (selectedTab < 3) key.removePrefix(prefix) else key

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
                            ) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { if (it.length <= 200) viewModel.updateCustomFieldValue(key, it) },
                                    label = { Text(if (isGlobal) "$cleanLabel (Global)" else cleanLabel) },
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
                                        onClick = { viewModel.removeCustomField(key) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    var newKey by remember { mutableStateOf("") }
                    var isGlobalToggle by remember { mutableStateOf(false) }
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

                    Button(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                val scopedKey = when (selectedScopeIndex) {
                                    0 -> "Top - ${newKey.trim()}"
                                    1 -> "Bottom - ${newKey.trim()}"
                                    2 -> "Dupatta - ${newKey.trim()}"
                                    else -> newKey.trim()
                                }
                                viewModel.addCustomField(scopedKey, isGlobalToggle)
                                newKey = ""
                                isGlobalToggle = false
                            }
                        },
                        modifier = Modifier.align(Alignment.End).minimumInteractiveComponentSize(),
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
            if (showSourceSelector) {
                AlertDialog(
                    onDismissRequest = { showSourceSelector = false },
                    title = { Text("Select Image Source", style = MaterialTheme.typography.titleLargeStrong) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)) {
                            Button(
                                onClick = {
                                    showSourceSelector = false
                                    imagePickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                                shape = AtelierShapes.CornerMedium
                            ) {
                                Text("Choose from Gallery")
                            }
                            
                            Button(
                                onClick = {
                                    showSourceSelector = false
                                    try {
                                        val uri = getTempUri(context)
                                        tempPhotoUriString = uri.toString()
                                        cameraLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                                shape = AtelierShapes.CornerMedium
                            ) {
                                Text("Take Photo with Camera")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showSourceSelector = false },
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

// FileUtils.copyUriToInternalStorage is used asynchronously

fun getTempUri(context: Context): Uri {
    val file = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
