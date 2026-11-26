package com.sc.biap.ui.components

// #region Imports
import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sc.biap.security.AppLock
import com.sc.biap.util.FileUtils
import com.sc.biap.util.VoiceNotes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// #endregion

/**
 * @file MediaAttachments.kt
 * @description A photograph and a spoken note, attached to whatever is being recorded.
 *
 * BUILT FOR A DEFECT, NOT FOR A GALLERY:
 *   The case this exists for is three pieces going back with something wrong with them. A
 *   photograph of the fault and four spoken seconds about it are worth more than any field
 *   asking somebody to describe it in writing - and far more likely to actually be filled in.
 *
 * ONE PHOTO, ONE NOTE. Not a list of either. A second photograph of the same fault adds
 * nothing, and the moment this becomes an album it becomes something to manage.
 */
@Composable
fun MediaAttachments(
    photoPath: String?,
    voicePath: String?,
    onPhotoChange: (String?) -> Unit,
    onVoiceChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        AppLock.endTrustedFlow()
        if (uri != null) {
            scope.launch { onPhotoChange(FileUtils.copyUriToInternalStorage(context, uri)) }
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        AppLock.endTrustedFlow()
        val uri = tempPhotoUri
        if (success && uri != null) {
            scope.launch { onPhotoChange(FileUtils.copyUriToInternalStorage(context, uri)) }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photoPath != null) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Attached photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.surface)
                        .clickable { onPhotoChange(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove photo",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurface,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    AppLock.beginTrustedFlow()
                    runCatching {
                        val uri = FileUtils.createCameraTempUri(context)
                        tempPhotoUri = uri
                        takePhoto.launch(uri)
                    }.onFailure { AppLock.endTrustedFlow() }
                },
                label = { Text(if (photoPath == null) "Photo" else "Retake") },
                leadingIcon = {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                },
            )
            AssistChip(
                onClick = {
                    AppLock.beginTrustedFlow()
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                label = { Text("Gallery") },
            )
        }

        VoiceNoteControl(
            voicePath = voicePath,
            onVoiceChange = onVoiceChange,
        )
    }
}

// #region The voice note

/**
 * Tap to record, tap to stop.
 *
 * Tap rather than hold. Holding is the gesture every chat app uses, but it means standing
 * still with a finger down while describing a fault with a garment in the other hand - and
 * letting go by accident loses the note entirely.
 */
@Composable
private fun VoiceNoteControl(
    voicePath: String?,
    onVoiceChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    val recording = recorder != null

    fun stopRecording(keep: Boolean) {
        val rec = recorder ?: return
        // stop() throws when nothing was captured - a tap-and-immediately-stop. Treat that
        // as "no note" rather than as a crash.
        val captured = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        recorder = null
        val file = recordingFile
        recordingFile = null
        if (keep && captured && file != null && file.length() > 0L) {
            onVoiceChange(file.absolutePath)
        } else {
            file?.delete()
        }
    }

    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLock.endTrustedFlow()
        if (granted) {
            val file = VoiceNotes.newFile(context)
            runCatching {
                VoiceNotes.recorder(context, file).apply { prepare(); start() }
            }.onSuccess {
                recorder = it
                recordingFile = file
                seconds = 0
            }.onFailure { file.delete() }
        }
    }

    // Ticks the counter while recording, and stops it running once it is not.
    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            seconds++
        }
    }

    // A recorder left running when the screen goes away keeps the microphone open.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { player?.release() }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            recording -> {
                AssistChip(
                    onClick = { stopRecording(keep = true) },
                    label = { Text("Stop  ${seconds}s") },
                    leadingIcon = {
                        Icon(Icons.Default.Stop, contentDescription = null, Modifier.size(18.dp))
                    },
                )
                Text(
                    "Recording",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }

            voicePath != null -> {
                val length = remember(voicePath) { VoiceNotes.durationSeconds(context, voicePath) }
                AssistChip(
                    onClick = {
                        if (playing) {
                            runCatching { player?.stop(); player?.release() }
                            player = null
                            playing = false
                        } else {
                            val file = VoiceNotes.resolve(context, voicePath)
                            if (file != null) {
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener {
                                            playing = false
                                            runCatching { release() }
                                            player = null
                                        }
                                        prepare()
                                        start()
                                    }
                                }.onSuccess { player = it; playing = true }
                            }
                        }
                    },
                    label = { Text(if (playing) "Stop" else if (length > 0) "Play  ${length}s" else "Play") },
                    leadingIcon = {
                        Icon(
                            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        runCatching { player?.release() }
                        player = null
                        playing = false
                        VoiceNotes.delete(context, voicePath)
                        onVoiceChange(null)
                    },
                    label = { Text("Delete note") },
                )
            }

            else -> AssistChip(
                onClick = {
                    AppLock.beginTrustedFlow()
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                label = { Text("Voice note") },
                leadingIcon = {
                    Icon(Icons.Default.Mic, contentDescription = null, Modifier.size(18.dp))
                },
            )
        }
    }
}
// #endregion

/** A note attached to something already saved: play it, nothing more. */
@Composable
fun VoiceNotePlayer(voicePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    val length = remember(voicePath) { VoiceNotes.durationSeconds(context, voicePath) }

    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() } }
    }

    AssistChip(
        modifier = modifier,
        onClick = {
            if (playing) {
                runCatching { player?.stop(); player?.release() }
                player = null
                playing = false
            } else {
                VoiceNotes.resolve(context, voicePath)?.let { file ->
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener {
                                playing = false
                                runCatching { release() }
                                player = null
                            }
                            prepare()
                            start()
                        }
                    }.onSuccess { player = it; playing = true }
                }
            }
        },
        label = { Text(if (playing) "Stop" else if (length > 0) "Play note  ${length}s" else "Play note") },
        leadingIcon = {
            Icon(
                if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                Modifier.size(18.dp),
            )
        },
    )
}
