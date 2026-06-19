package com.example.biap.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.example.biap.data.DefaultDataRepository
import com.example.biap.data.Design
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

    var showDeleteDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Design Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when (val uiState = state) {
            DesignDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            DesignDetailUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Design not found", style = MaterialTheme.typography.titleMedium)
                }
            }
            is DesignDetailUiState.Success -> {
                val design = uiState.design
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Design Image
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
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (design.imagePath != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(design.imagePath)),
                                contentDescription = design.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No Photo Available", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Product Title & Category
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

                    // Sizes Table
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

                    // Assigned Karigars Section
                    var showEditKarigarsDialog by remember { mutableStateOf(false) }

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

                            if (design.karigars.isEmpty()) {
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
                                    design.karigars.forEach { karigar ->
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
                        var tempKarigars by remember(design.karigars) { mutableStateOf(design.karigars) }
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
                                        viewModel.updateKarigars(tempKarigars)
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

                    // Custom Attributes (Specifications)
                    if (design.customFields.isNotEmpty()) {
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

                                val filteredSpecs = design.customFields.filter { (key, _) ->
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
                        }
                    }
                }
            }
        }
    }
}
