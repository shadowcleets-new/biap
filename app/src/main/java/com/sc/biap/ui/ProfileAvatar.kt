package com.sc.biap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Scale

/**
 * @file ProfileAvatar.kt
 * @description The signed-in person's picture, or a stand-in when there isn't one.
 *
 * WHY THIS IS NOT JUST AN Image():
 *   A Coil painter renders NOTHING while loading and nothing again if the load fails. Wrapped
 *   in an IconButton that produces an invisible but still tappable control - which is exactly
 *   how this looked after signing in with Google: a blank gap in the toolbar with a working
 *   button hiding in it.
 *
 *   The immediate cause was a missing dependency (Coil 3 keeps network fetching in a separate
 *   artifact, so every https URL failed while local files loaded fine). That is fixed, but the
 *   failure mode is worth closing regardless: a photo can also fail because the device is
 *   offline or because Google's URL has expired, and neither should leave a hole where a
 *   button ought to be.
 */
@Composable
fun ProfileAvatar(
    photoUrl: Any?,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (photoUrl == null) {
        Fallback(size, modifier, tint)
        return
    }

    // Sized explicitly, because this one cannot be an AsyncImage: the decision below
    // needs the painter's own state, and AsyncImage does not expose it. Left unsized, Coil
    // decodes at the source resolution - for a Google account photo that is far more than
    // the few dozen dp this draws into. Scale.FILL matches ContentScale.Crop below.
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(photoUrl, size, density, context) {
        val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(photoUrl)
            .size(px, px)
            .scale(Scale.FILL)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()

    // Only Success draws the photo. Loading, Error and Empty all fall through to the icon,
    // so there is never a frame where the control is invisible.
    if (state is AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Fallback(size, modifier, tint)
    }
}

@Composable
private fun Fallback(size: Dp, modifier: Modifier, tint: Color) {
    Icon(
        Icons.Default.AccountCircle,
        contentDescription = "Profile",
        modifier = modifier.size(size),
        tint = tint,
    )
}
