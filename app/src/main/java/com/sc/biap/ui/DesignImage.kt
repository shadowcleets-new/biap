package com.sc.biap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sc.biap.util.FileUtils
import com.sc.biap.util.ImageStore

/**
 * @file DesignImage.kt
 * @description Resolves what a design's photo should actually load from.
 *
 * Precedence is local-first, and deliberately so:
 *
 *  1. the local file, when it exists - the device that shot the photo has it immediately,
 *     it works with no signal, and it means the common path never waits on the network
 *  2. otherwise the uploaded version, resolved from `imageRef` - this is what lets an
 *     employee see a photo somebody else took
 *
 * The remote URL is only resolved when there is no local file, so a design whose photo was
 * taken on this device costs zero network calls. Coil caches the fetched bytes on disk, so
 * a photo taken by someone else downloads once per device rather than once per view.
 *
 * Returns a value Coil accepts directly: a File, a URL String, or null for "no image".
 */
@Composable
fun rememberDesignImageModel(imagePath: String?, imageRef: String?): Any? {
    val context = LocalContext.current

    val localFile = remember(imagePath) {
        FileUtils.resolveImageFile(context, imagePath)?.takeIf { it.exists() }
    }
    if (localFile != null) return localFile

    // No local copy: fall back to the uploaded version. Keyed on imageRef, so replacing a
    // photo produces a new key, a new URL, and therefore a genuine cache miss rather than a
    // stale image served forever.
    var remoteUrl by remember(imageRef) { mutableStateOf<String?>(null) }
    LaunchedEffect(imageRef) {
        remoteUrl = imageRef?.takeIf { it.isNotBlank() }?.let { ImageStore.resolveUrl(it) }
    }
    return remoteUrl
}
