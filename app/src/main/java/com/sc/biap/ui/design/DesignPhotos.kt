package com.sc.biap.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.Design
import com.sc.biap.data.DesignPhoto
import com.sc.biap.ui.DesignImageState
import com.sc.biap.ui.rememberDesignImage

/**
 * @file DesignPhotos.kt
 * @description Showing and choosing a design's photographs, up to [Design.MAX_PHOTOS] of them.
 *
 * [TABLE OF CONTENTS]
 * 1. LOCAL CONSTANTS & CONFIG
 * 2. ONE PHOTOGRAPH
 * 3. THE PAGER
 * 4. THE STRIP
 * 5. THE EMPTY PANEL
 *
 * NOTHING HERE CROPS.
 *   Every frame uses ContentScale.Fit. The detail screen used Crop against a fixed 280dp
 *   height, which on a full-length shot of a garment cut off the head and the hem - the two
 *   ends of the thing being looked at, and on this screen the whole point of looking. Fit
 *   letterboxes instead, onto the panel's own background, so a photo of any proportion
 *   arrives whole.
 *
 *   The small tiles in the strip are the deliberate exception in spirit only: they are 3:4,
 *   which is the proportion these photographs are actually taken in, so Fit fills them almost
 *   exactly anyway.
 */

// #region 1. Local constants & config

/** Tall enough to show a full-length garment without dominating the screen. */
private val PagerHeight = 400.dp
private val ThumbWidth = 68.dp
private val ThumbRadius = 16.dp
private val ThumbAspect = 3f / 4f
private val RemoveBadge = 22.dp

// #endregion

// #region 2. One photograph

/**
 * One photo, drawn whole, or an honest account of why it is not there.
 *
 * The four states are the ones [rememberDesignImage] distinguishes, and the distinction that
 * matters is Unavailable against Absent: a photo that could not be fetched is still safely in
 * Drive, and saying "no photo" would send somebody off to re-shoot a design that never needed
 * it.
 */
@Composable
fun DesignPhotoFrame(
    photo: DesignPhoto,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    val image = rememberDesignImage(photo.localPath, photo.ref)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val state = image.state) {
            is DesignImageState.Ready -> Image(
                painter = rememberAsyncImagePainter(state.model),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // The whole photograph, never a crop of it. See the file header.
                contentScale = ContentScale.Fit,
            )

            DesignImageState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
            )

            DesignImageState.Unavailable -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { image.retry() }.padding(8.dp),
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Photo unavailable - tap to retry",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            DesignImageState.Absent -> Icon(
                Icons.Default.PhotoCamera,
                contentDescription = "No photo",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// #endregion

// #region 3. The pager

/**
 * Swipe through a design's photographs, with dots when there is more than one.
 *
 * No dots for a single photo: an indicator showing one position tells you nothing and only
 * suggests there is something to swipe to.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignPhotoPager(
    photos: List<DesignPhoto>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    page: Int = 0,
    onPageChange: (Int) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val state = rememberPagerState(initialPage = page.coerceIn(0, maxOf(0, photos.size - 1))) {
        photos.size
    }

    // Driven both ways: swiping tells the strip which tile to highlight, and tapping a tile
    // moves the pager. Without the second half the strip would be a read-out rather than a
    // control.
    LaunchedEffect(page) {
        if (page in photos.indices && page != state.currentPage) state.animateScrollToPage(page)
    }
    LaunchedEffect(state.currentPage) { onPageChange(state.currentPage) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PagerHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh),
        ) {
            HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { index ->
                DesignPhotoFrame(
                    photo = photos[index],
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 40.dp,
                )
            }
        }

        if (photos.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(photos.size) { index ->
                    val active = index == state.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) scheme.primary else scheme.outlineVariant),
                    )
                }
            }
        }
    }
}

// #endregion

// #region 4. The strip

/**
 * The row of thumbnails, and the tile that adds another.
 *
 * The add tile disappears at [Design.MAX_PHOTOS] rather than staying and refusing - a control
 * that is present but rejects every tap is worse than one that is not there. The count beside
 * it says how much room is left, so the disappearance is never a surprise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignPhotoStrip(
    photos: List<DesignPhoto>,
    editable: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Int = -1,
    onSelect: (Int) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val canAdd = editable && photos.size < Design.MAX_PHOTOS

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(photos, key = { index, photo -> "${index}_${photo.localPath}_${photo.ref}" }) { index, photo ->
            Box {
                Box(
                    modifier = Modifier
                        .width(ThumbWidth)
                        .aspectRatio(ThumbAspect)
                        .clip(RoundedCornerShape(ThumbRadius))
                        .background(scheme.surfaceContainerHighest)
                        .then(
                            if (index == selected) {
                                Modifier.border(2.dp, scheme.primary, RoundedCornerShape(ThumbRadius))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(index) },
                ) {
                    DesignPhotoFrame(
                        photo = photo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 20.dp,
                    )
                    if (index == 0 && photos.size > 1) {
                        // Which photo is the cover is not cosmetic - it is the one the
                        // catalogue, the order carousel and every thumbnail will use.
                        Text(
                            "COVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(vertical = 2.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (editable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(RemoveBadge)
                            .clip(CircleShape)
                            .background(scheme.errorContainer)
                            .clickable { onRemove(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove photo ${index + 1}",
                            tint = scheme.onErrorContainer,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        if (canAdd) {
            item(key = "add") {
                Column(
                    modifier = Modifier
                        .width(ThumbWidth)
                        .aspectRatio(ThumbAspect)
                        .clip(RoundedCornerShape(ThumbRadius))
                        .background(scheme.surfaceContainerHigh)
                        .clickable(onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add photo",
                        tint = scheme.onSurfaceVariant,
                    )
                    Text(
                        "${photos.size}/${Design.MAX_PHOTOS}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// #endregion

// #region 5. The empty panel

/** The full-width invitation shown before there is any photograph at all. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesignPhotoEmptyPanel(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Add design photos", style = MaterialTheme.typography.titleMedium)
        Text(
            "Up to ${Design.MAX_PHOTOS}. The first is the cover.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

// #endregion
