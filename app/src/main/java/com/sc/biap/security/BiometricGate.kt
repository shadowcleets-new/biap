package com.sc.biap.security

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.titleMediumStrong

private val LockIconSize = 48.dp

/**
 * @file BiometricGate.kt
 * @description Covers the app until the person holding the phone proves who they are.
 *
 * Wraps rather than replaces the content: the app below stays composed, so unlocking is
 * instant and no screen state is lost. The cover is opaque, so nothing underneath is
 * readable while locked.
 */
@Composable
fun BiometricGate(content: @Composable () -> Unit) {
    val isLocked by AppLock.isLocked.collectAsStateWithLifecycle()

    content()

    if (isLocked) {
        LockCover()
    }
}

@Composable
private fun LockCover() {
    val context = LocalContext.current
    // Tracks whether a prompt is already on screen, so recomposition cannot stack two.
    var prompting by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val showPrompt: () -> Unit = show@{
        if (prompting) return@show
        val activity = context as? FragmentActivity ?: run {
            // Nothing to attach a prompt to; refusing to unlock would strand the user.
            AppLock.unlock()
            return@show
        }
        prompting = true

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    lastError = null
                    AppLock.unlock()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    prompting = false
                    // Cancelling leaves the app locked with an Unlock button, rather than
                    // re-prompting in a loop the user cannot escape.
                    lastError = when (code) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> null
                        else -> message.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    // A single unrecognised finger. The prompt stays up and handles retries.
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock BIAP")
                .setSubtitle("Your designs, stock and pricing are protected")
                .setAllowedAuthenticators(AppLock.ALLOWED_AUTHENTICATORS)
                .build()
        )
    }

    // Prompt as soon as the cover appears, so the common case is a single glance.
    LaunchedEffect(Unit) { showPrompt() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // An opaque background hides the app but does NOT stop input reaching it -
            // taps, drags and scrolls would all still land on the catalogue underneath,
            // which would make this a picture of a lock rather than a lock. Consume every
            // pointer event so nothing below is reachable while locked.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base),
            modifier = Modifier.padding(AtelierSpacing.Large)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(LockIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("BIAP is locked", style = MaterialTheme.typography.titleMediumStrong)
            Text(
                lastError ?: "Unlock with your fingerprint, face or screen lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = showPrompt) { Text("Unlock") }
        }
    }
}
