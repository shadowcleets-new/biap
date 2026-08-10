package com.sc.biap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sc.biap.util.FileUtils
import com.sc.biap.util.ImageStore

/**
 * @file DesignImage.kt
 * @description Resolves what a design's photo should load from, and says so honestly
 *              when it cannot.
 *
 * PRECEDENCE IS LOCAL-FIRST, DELIBERATELY:
 *  1. the local file, when it exists - the device that shot the photo has it immediately,
 *     it works with no signal, and the common path never waits on the network
 *  2. otherwise the uploaded version, resolved from `imageRef` - this is what lets an
 *     employee see a photo somebody else took
 *
 * The remote URL is only resolved when there is no local file, so a photo taken on this
 * device costs zero network calls. Coil caches the fetched bytes, and ImageStore caches the
 * resolved URL, so a photo taken by someone else costs one round trip per device.
 *
 * WHY THIS RETURNS A STATE RATHER THAN A NULLABLE MODEL:
 *   It used to return null for both "this design has no photo" and "the photo exists but
 *   could not be fetched". The screens drew their empty state for null, so a fetch that was
 *   refused - an expired session, no signal, a rejected App Check token - looked exactly
 *   like a design nobody had photographed. The photo appeared to have been lost when it was
 *   sitting safely in Storage the whole time, and nothing anywhere said otherwise.
 *
 *   Those two cases are now distinct, and a failure is retryable instead of permanent: the
 *   old version resolved once per `imageRef` and never tried again, so a single transient
 *   failure left the photo blank for as long as the screen stayed open.
 */

@Immutable
sealed interface DesignImageState {
    /** No photo has ever been attached to this design. */
    data object Absent : DesignImageState

    /** A photo exists remotely and its URL is being resolved. */
    data object Loading : DesignImageState

    /** Ready to draw. [model] is a File or a URL String - both accepted by Coil directly. */
    data class Ready(val model: Any) : DesignImageState

    /**
     * A photo exists but could not be fetched right now. Not the same as [Absent]:
     * the bytes are in Storage, this device just could not reach them.
     */
    data object Unavailable : DesignImageState
}

@Stable
class DesignImage internal constructor(
    val state: DesignImageState,
    /** Re-attempts resolution. Safe to call repeatedly; ignored unless currently failed. */
    val retry: () -> Unit
)

@Composable
fun rememberDesignImage(imagePath: String?, imageRef: String?): DesignImage {
    val context = LocalContext.current

    // Reset the retry counter whenever the design points somewhere new, so a fresh photo
    // starts from a clean slate rather than inheriting a previous failure's attempts.
    val attempts = remember(imagePath, imageRef) { mutableIntStateOf(0) }

    val localFile = remember(imagePath) {
        FileUtils.resolveImageFile(context, imagePath)?.takeIf { it.exists() }
    }
    val remoteKey = imageRef?.takeIf { it.isNotBlank() }

    var resolved by remember(remoteKey) { mutableStateOf<String?>(null) }
    var failed by remember(remoteKey) { mutableStateOf(false) }

    // Keyed on attempts as well as the ref, so retry() genuinely re-runs the lookup.
    LaunchedEffect(remoteKey, localFile, attempts.intValue) {
        if (localFile != null || remoteKey == null) return@LaunchedEffect
        failed = false
        val url = ImageStore.resolveUrl(remoteKey)
        if (url != null) resolved = url else failed = true
    }

    val state = when {
        localFile != null -> DesignImageState.Ready(localFile)
        remoteKey == null -> DesignImageState.Absent
        resolved != null -> DesignImageState.Ready(resolved!!)
        failed -> DesignImageState.Unavailable
        else -> DesignImageState.Loading
    }

    val retry = remember(attempts) { { attempts.intValue += 1 } }
    return remember(state, retry) { DesignImage(state, retry) }
}
