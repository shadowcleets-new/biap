package com.example.biap.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil3.compose.rememberAsyncImagePainter
import com.example.biap.AddDesign
import com.example.biap.DesignDetail
import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.DefaultDataRepository
import com.example.biap.data.Design
import com.example.biap.theme.GeminiTheme
import java.io.File

@Composable
fun defaultMainScreenViewModel(): MainScreenViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { MainScreenViewModel(DefaultDataRepository(context)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = defaultMainScreenViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Portfolio", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onItemClick(AddDesign) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Design")
            }
        },
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
                MainScreenContent(
                    data = data,
                    onItemClick = onItemClick,
                    modifier = Modifier.padding(paddingValues)
                )
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
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = if (isSystemInDarkTheme()) GeminiTheme.darkGlowBrush else GeminiTheme.lightGlowBrush,
                            shape = CircleShape,
                            alpha = 0.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = "Empty stock",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "No designs added yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the + button to upload design photos, set prices by size, and add custom fields.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(280.dp),
                    onTextLayout = {}
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
        prices.size == 1 -> "₹${prices.first()}"
        else -> "₹${prices.minOrNull()} - ₹${prices.maxOrNull()}"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )

    val isDark = isSystemInDarkTheme()
    val borderBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFF9EC5FF).copy(alpha = 0.2f),
                Color(0xFFC5A3FF).copy(alpha = 0.2f),
                Color(0xFF80DEEA).copy(alpha = 0.2f)
            )
        } else {
            listOf(
                Color(0xFF1A73E8).copy(alpha = 0.15f),
                Color(0xFF9C27B0).copy(alpha = 0.15f),
                Color(0xFF00E5FF).copy(alpha = 0.15f)
            )
        }
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderBrush),
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
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0xFF9EC5FF).copy(alpha = 0.12f),
                                    Color(0xFFC5A3FF).copy(alpha = 0.12f),
                                    Color(0xFF80DEEA).copy(alpha = 0.12f)
                                )
                            } else {
                                listOf(
                                    Color(0xFF1A73E8).copy(alpha = 0.08f),
                                    Color(0xFF9C27B0).copy(alpha = 0.08f),
                                    Color(0xFF00E5FF).copy(alpha = 0.08f)
                                )
                            }
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
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = "No photo",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = design.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
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
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
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
