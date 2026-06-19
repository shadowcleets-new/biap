package com.example.biap.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

enum class PerformanceProfile {
    HIGH_FIDELITY,
    LOW_POWER
}

@Composable
fun rememberPerformanceProfile(): PerformanceProfile {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    
    // Check CPU concurrency: low-end profiles if 4 or fewer processors are available
    val isLowEndHardware = remember { Runtime.getRuntime().availableProcessors() <= 4 }
    
    var isPowerSaveMode by remember {
        mutableStateOf(powerManager?.isPowerSaveMode ?: false)
    }
    
    if (powerManager != null) {
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    isPowerSaveMode = powerManager.isPowerSaveMode
                }
            }
            
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            
            // On API 33+ (Android 13+), system broadcasts do not require flags,
            // but registering with RECEIVER_NOT_EXPORTED is standard for local/system events.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            
            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore unregistration errors
                }
            }
        }
    }
    
    return remember(isPowerSaveMode, isLowEndHardware) {
        if (isPowerSaveMode || isLowEndHardware) {
            PerformanceProfile.LOW_POWER
        } else {
            PerformanceProfile.HIGH_FIDELITY
        }
    }
}
