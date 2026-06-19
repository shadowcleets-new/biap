package com.example.biap.ui.design

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
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
import com.example.biap.data.SizeInventory
import java.io.File

@Composable
fun defaultAddDesignViewModel(): AddDesignViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { AddDesignViewModel(DefaultDataRepository(context)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDesignScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddDesignViewModel = defaultAddDesignViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = copyUriToInternalStorage(context, uri)
            viewModel.onImageSelected(localPath)
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Design", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveDesign() },
                        enabled = state.name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Image Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (state.imagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(File(state.imagePath!!)),
                        contentDescription = "Design Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
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

            // Design Details
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Basic Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.onNameChange(it) },
                        label = { Text("Design Name / Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = state.category,
                        onValueChange = { viewModel.onCategoryChange(it) },
                        label = { Text("Category (e.g. Salwar Kameez)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Size Stock and Pricing Matrix
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Sizes, Stock & Prices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Configure stock and unique pricing per size.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val standardSizes = listOf("S", "M", "L", "XL", "XXL")

                    standardSizes.forEach { size ->
                        val inventory = state.sizes.find { it.size == size }
                        val isEnabled = inventory != null

                        var stockText by remember(inventory) { mutableStateOf(inventory?.stock?.toString() ?: "") }
                        var priceText by remember(inventory) { mutableStateOf(inventory?.price?.toString() ?: "") }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isEnabled,
                                onClick = {
                                    val currentStock = stockText.toIntOrNull() ?: 0
                                    val currentPrice = priceText.toDoubleOrNull() ?: 0.0
                                    viewModel.updateSizeInventory(size, currentStock, currentPrice, !isEnabled)
                                },
                                label = { Text(size, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.width(64.dp)
                            )

                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                        stockText = newValue
                                        if (isEnabled) {
                                            viewModel.updateSizeInventory(
                                                size,
                                                newValue.toIntOrNull() ?: 0,
                                                priceText.toDoubleOrNull() ?: 0.0,
                                                true
                                            )
                                        }
                                    }
                                },
                                label = { Text("Stock") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                enabled = isEnabled,
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toDoubleOrNull() != null) {
                                        priceText = newValue
                                        if (isEnabled) {
                                            viewModel.updateSizeInventory(
                                                size,
                                                stockText.toIntOrNull() ?: 0,
                                                newValue.toDoubleOrNull() ?: 0.0,
                                                true
                                            )
                                        }
                                    }
                                },
                                label = { Text("Price") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                enabled = isEnabled,
                                shape = RoundedCornerShape(8.dp),
                                prefix = { Text("₹") }
                            )
                        }
                    }
                }
            }

            // Custom Attributes & Global Fields
            // Custom Attributes & Global Fields
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("3-Piece Attributes & Custom Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    val attributeTabs = listOf("Top (Kameez)", "Bottom (Salwar)", "Dupatta", "General")
                    var selectedTab by remember { mutableStateOf(0) }

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 0.dp,
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
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        filteredFields.forEach { (key, value) ->
                            val isGlobal = state.globalCustomFields.contains(key)
                            val cleanLabel = if (selectedTab < 3) key.removePrefix(prefix) else key

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { viewModel.updateCustomFieldValue(key, it) },
                                    label = { Text(if (isGlobal) "$cleanLabel (Global)" else cleanLabel) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val defaultFields = setOf(
                                    "Top - Fabric", "Top - Work", "Top - Color",
                                    "Bottom - Fabric", "Bottom - Work", "Bottom - Color",
                                    "Dupatta - Fabric", "Dupatta - Work", "Dupatta - Color"
                                )
                                if (!defaultFields.contains(key)) {
                                    IconButton(onClick = { viewModel.removeCustomField(key) }) {
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

                    Text("Add New Custom Field", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
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
                            onCheckedChange = { isGlobalToggle = it }
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
}

fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
