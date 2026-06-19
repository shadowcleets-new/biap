package com.example.biap.ui.design

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
import com.example.biap.data.DefaultDataRepository
import com.example.biap.data.Design
import com.example.biap.data.SizeInventory
import java.io.File

@Composable
fun defaultDesignDetailViewModel(designId: String): DesignDetailViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { DesignDetailViewModel(designId, DefaultDataRepository(context)) }
}

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
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = copyUriToInternalStorage(context, uri)
            editImagePath = localPath
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoUri != null) {
            val localPath = copyUriToInternalStorage(context, tempPhotoUri!!)
            editImagePath = localPath
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Design") },
            text = { Text("Are you sure you want to delete this design? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDesign()
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    when (val uiState = state) {
        DesignDetailUiState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Loading...", fontWeight = FontWeight.Bold) },
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
                    CircularProgressIndicator()
                }
            }
        }
        DesignDetailUiState.NotFound -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Not Found", fontWeight = FontWeight.Bold) },
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
                    Text("Design not found", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        is DesignDetailUiState.Success -> {
            val design = uiState.design

            // Sync with actual design when edit mode is activated
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    editName = design.name
                    editCategory = design.category
                    editImagePath = design.imagePath
                    editSizes = design.sizeInventories
                    editCustomFields = design.customFields
                    editKarigars = design.karigars
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (isEditing) "Edit Design" else "Design Details", fontWeight = FontWeight.Bold) },
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
                                            val updated = design.copy(
                                                name = editName.trim(),
                                                category = editCategory.trim(),
                                                imagePath = editImagePath,
                                                sizeInventories = editSizes,
                                                customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                                karigars = editKarigars
                                            )
                                            viewModel.updateDesign(updated)
                                            isEditing = false
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Design Image Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                )
                            )
                            .clickable(enabled = isEditing) { showSourceSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val imagePathToUse = if (isEditing) editImagePath else design.imagePath
                        if (imagePathToUse != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(imagePathToUse)),
                                contentDescription = if (isEditing) "Edit Photo" else design.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = "Change Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Upload Photo",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Upload Design Photo", fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Product Title & Category Card
                    if (isEditing) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Basic Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { if (it.length <= 80) editName = it },
                                    label = { Text("Design Name / Code") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = editCategory,
                                    onValueChange = { if (it.length <= 80) editCategory = it },
                                    label = { Text("Category (e.g. Salwar Kameez)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = design.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(design.category) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Inventory & Pricing Card
                    if (isEditing) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Sizes, Stock & Prices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                
                                var showCustomSizeDialog by remember { mutableStateOf(false) }
                                var newSizeInput by remember { mutableStateOf("") }
                                val availableSizes = remember { mutableStateListOf("S", "M", "L", "XL", "XXL") }
                                
                                LaunchedEffect(editSizes) {
                                    editSizes.forEach {
                                        if (!availableSizes.contains(it.size)) {
                                            availableSizes.add(it.size)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    availableSizes.forEach { size ->
                                        val isEnabled = editSizes.any { it.size == size }
                                        FilterChip(
                                            selected = isEnabled,
                                            onClick = {
                                                val updated = editSizes.toMutableList()
                                                if (isEnabled) {
                                                    updated.removeAll { it.size == size }
                                                } else {
                                                    updated.add(SizeInventory(size, 0, 0.0))
                                                }
                                                editSizes = updated
                                            },
                                            label = { Text(size, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                    InputChip(
                                        selected = false,
                                        onClick = { showCustomSizeDialog = true },
                                        label = { Text("+ Custom") }
                                    )
                                }

                                if (editSizes.isEmpty()) {
                                    Text(
                                        "Select sizes above to configure stock and pricing.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    editSizes.sortedBy { it.size }.forEach { inv ->
                                        var stockText by remember(inv) { mutableStateOf(inv.stock.toString()) }
                                        var priceText by remember(inv) { mutableStateOf(inv.price.toString()) }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(56.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(inv.size, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                                                            editSizes = updated
                                                        }
                                                    }
                                                },
                                                label = { Text("Stock") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            OutlinedTextField(
                                                value = priceText,
                                                onValueChange = { newValue ->
                                                    val dotCount = newValue.count { it == '.' }
                                                    val sanitized = newValue.filter { it.isDigit() || it == '.' }
                                                    if (dotCount <= 1 && sanitized.length <= 9) {
                                                        priceText = sanitized
                                                        val updated = editSizes.toMutableList()
                                                        val idx = updated.indexOfFirst { it.size == inv.size }
                                                        if (idx >= 0) {
                                                            updated[idx] = inv.copy(price = sanitized.toDoubleOrNull() ?: 0.0)
                                                            editSizes = updated
                                                        }
                                                    }
                                                },
                                                label = { Text("Price") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                prefix = { Text("₹") }
                                            )
                                        }
                                    }
                                }

                                if (showCustomSizeDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showCustomSizeDialog = false },
                                        title = { Text("Add Custom Size", fontWeight = FontWeight.Bold) },
                                        text = {
                                            OutlinedTextField(
                                                value = newSizeInput,
                                                onValueChange = { if (it.length <= 10) newSizeInput = it },
                                                label = { Text("Size Label (e.g. 3XL)") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    if (newSizeInput.isNotBlank()) {
                                                        val norm = newSizeInput.trim().uppercase()
                                                        if (!availableSizes.contains(norm)) {
                                                            availableSizes.add(norm)
                                                        }
                                                        val updated = editSizes.toMutableList()
                                                        if (!updated.any { it.size == norm }) {
                                                            updated.add(SizeInventory(norm, 0, 0.0))
                                                            editSizes = updated
                                                        }
                                                        newSizeInput = ""
                                                        showCustomSizeDialog = false
                                                    }
                                                }
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
                    } else {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Inventory & Pricing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                if (design.sizeInventories.isEmpty()) {
                                    Text(
                                        "No size options configured for this design.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val sortedInventories = design.sizeInventories.sortedBy { it.size }
                                    var selectedSizeIndex by remember(design) { mutableStateOf(0) }
                                    
                                    if (selectedSizeIndex >= sortedInventories.size) {
                                        selectedSizeIndex = 0
                                    }
                                    
                                    val activeInventory = sortedInventories.getOrNull(selectedSizeIndex)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sortedInventories.forEachIndexed { index, inv ->
                                            val isSelected = selectedSizeIndex == index
                                            InputChip(
                                                selected = isSelected,
                                                onClick = { selectedSizeIndex = index },
                                                label = { Text(inv.size, fontWeight = FontWeight.Bold) },
                                                colors = InputChipDefaults.inputChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    if (activeInventory != null) {
                                        val stockColor = when {
                                            activeInventory.stock == 0 -> MaterialTheme.colorScheme.error
                                            activeInventory.stock <= 5 -> Color(0xFFFF9800) // Orange
                                            else -> Color(0xFF4CAF50) // Green
                                        }

                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = "₹${activeInventory.price}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Availability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = if (activeInventory.stock == 0) "Out of Stock" else "${activeInventory.stock} items",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
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

                    // Assigned Karigars Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Assigned Karigars", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showEditKarigarsDialog = true }) {
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
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    karigarsListToUse.forEach { karigar ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(karigar, fontWeight = FontWeight.Medium) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showEditKarigarsDialog) {
                        val karigarsListToUse = if (isEditing) editKarigars else design.karigars
                        var tempKarigars by remember(karigarsListToUse) { mutableStateOf(karigarsListToUse) }
                        var newKarigarInput by remember { mutableStateOf("") }

                        AlertDialog(
                            onDismissRequest = { showEditKarigarsDialog = false },
                            title = { Text("Manage Karigars", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = newKarigarInput,
                                        onValueChange = { if (it.length <= 50) newKarigarInput = it },
                                        label = { Text("Karigar Name") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
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
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            tempKarigars.forEach { karigar ->
                                                InputChip(
                                                    selected = false,
                                                    onClick = {},
                                                    label = { Text(karigar) },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Remove",
                                                            modifier = Modifier
                                                                .size(18.dp)
                                                                .clickable { tempKarigars = tempKarigars - karigar }
                                                        )
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
                                            editKarigars = tempKarigars
                                        } else {
                                            viewModel.updateKarigars(tempKarigars)
                                        }
                                        showEditKarigarsDialog = false
                                    }
                                ) {
                                    Text("Save Changes")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditKarigarsDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Specifications Card
                    val specsToUse = if (isEditing) editCustomFields else design.customFields

                    if (specsToUse.isNotEmpty() || isEditing) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                val specTabs = listOf("Top (Kameez)", "Bottom (Salwar)", "Dupatta", "General")
                                var selectedSpecTab by remember { mutableStateOf(0) }

                                ScrollableTabRow(
                                    selectedTabIndex = selectedSpecTab,
                                    edgePadding = 0.dp,
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
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp, vertical = 6.dp)
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
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    filteredSpecs.forEach { (key, value) ->
                                        val cleanLabel = if (selectedSpecTab < 3) key.removePrefix(prefix) else key
                                        if (isEditing) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = value,
                                                    onValueChange = { newValue ->
                                                        if (newValue.length <= 200) {
                                                            val updated = editCustomFields.toMutableMap()
                                                            updated[key] = newValue
                                                            editCustomFields = updated
                                                        }
                                                    },
                                                    label = { Text(cleanLabel) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
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
                                                            editCustomFields = updated
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
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = cleanLabel,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = value.ifBlank { "Not Specified" },
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isEditing) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    
                                    var newKey by remember { mutableStateOf("") }
                                    var selectedScopeIndex by remember { mutableStateOf(0) }
                                    val scopes = listOf("Top", "Bottom", "Dupatta", "General")

                                    Text("Add New Custom Field", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                                    OutlinedTextField(
                                        value = newKey,
                                        onValueChange = { if (it.length <= 50) newKey = it },
                                        label = { Text("Field Name (e.g. Sleeve Style, Lining)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Text("Apply Field To:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    editCustomFields = updated
                                                }
                                                newKey = ""
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add")
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add Field")
                                    }
                                }
                            }
                        }
                    }

                    if (showSourceSelector) {
                        AlertDialog(
                            onDismissRequest = { showSourceSelector = false },
                            title = { Text("Select Image Source", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            showSourceSelector = false
                                            imagePickerLauncher.launch(
                                                androidx.activity.result.PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Choose from Gallery")
                                    }
                                    
                                    Button(
                                        onClick = {
                                            showSourceSelector = false
                                            try {
                                                val uri = getTempUri(context)
                                                tempPhotoUri = uri
                                                cameraLauncher.launch(uri)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Take Photo with Camera")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSourceSelector = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
