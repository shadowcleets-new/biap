package com.sc.biap.ui.hub

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// #endregion

/**
 * @file BusinessHubMenu.kt
 * @description The Business Hub's front door: two doors rather than one long screen.
 *
 * WHY THIS EXISTS:
 *   Everything the Hub holds had been stacked onto one scrolling screen - the karigar pool,
 *   what is out with them, and the activity board - and the owner had to get past all of it
 *   to reach any of it. The two halves are also read at completely different rhythms: who
 *   does what is a standing arrangement set up once, while what is out today changes daily.
 *   Putting them behind one tap each costs a tap and saves a scroll past something that was
 *   never the thing being looked for.
 *
 * Each row says what is behind it rather than only naming it, because "Work management" on
 * its own does not distinguish itself from "Karigar and activities management" to anyone who
 * has not already used both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessHubScreen(
    onBack: () -> Unit,
    onKarigarsAndActivities: () -> Unit,
    onWorkManagement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessHubViewModel = defaultBusinessHubViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val outWithKarigars by viewModel.outWithKarigars.collectAsStateWithLifecycle()

    val people = state.karigars.size
    val activities = state.activities.size
    val piecesOut = outWithKarigars.sumOf { it.pieces }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Business Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HubMenuRow(
                icon = Icons.Default.Groups,
                title = "Karigar and activities management",
                body = "Who works here and what each of them does.",
                // The standing arrangement, stated as counts so the row says whether it has
                // been set up without the owner having to open it to find out.
                status = when {
                    people == 0 -> "No karigars yet"
                    else -> "$people ${if (people == 1) "karigar" else "karigars"} · " +
                        "$activities ${if (activities == 1) "activity" else "activities"}"
                },
                onClick = onKarigarsAndActivities,
            )
            HubMenuRow(
                icon = Icons.Default.LocalShipping,
                title = "Work management",
                body = "Pieces out with karigars, and how long they have been gone.",
                status = when {
                    piecesOut == 0 -> "Nothing out"
                    else -> "$piecesOut ${if (piecesOut == 1) "piece" else "pieces"} out " +
                        "with ${outWithKarigars.size} ${if (outWithKarigars.size == 1) "karigar" else "karigars"}"
                },
                // Something overdue is the one fact worth carrying to the front door, since
                // the whole point of the screen behind it is noticing before being told.
                alert = outWithKarigars.any { it.oldestDaysOut >= com.sc.biap.data.JOB_OVERDUE_DAYS },
                onClick = onWorkManagement,
            )
        }
    }
}

// #region Pieces

@Composable
private fun HubMenuRow(
    icon: ImageVector,
    title: String,
    body: String,
    status: String,
    onClick: () -> Unit,
    alert: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (alert) scheme.errorContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (alert) scheme.onErrorContainer else scheme.onSurface
        val quietInk = if (alert) scheme.onErrorContainer else scheme.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
            Spacer(Modifier.size(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = quietInk)
            Spacer(Modifier.size(6.dp))
            Text(status, style = MaterialTheme.typography.labelLarge, color = ink)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = quietInk,
        )
    }
}
// #endregion
