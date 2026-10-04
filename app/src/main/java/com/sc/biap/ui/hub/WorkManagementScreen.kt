package com.sc.biap.ui.hub

// #region Imports
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sc.biap.data.JOB_OVERDUE_DAYS
import com.sc.biap.data.KarigarLoad
// #endregion

/**
 * @file WorkManagementScreen.kt
 * @description Where the goods are: pieces that have left the building, and who has them.
 *
 * Split out of the Hub board because the two answer different questions. That board is a
 * standing arrangement - who does what - edited rarely. This is today's state of play, read
 * often and changing daily, and burying it under a board the owner had already finished
 * setting up made it something they had to scroll past rather than something they could
 * open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessHubViewModel = defaultBusinessHubViewModel(),
) {
    val outWithKarigars by viewModel.outWithKarigars.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Work management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "out-heading") {
                HubSectionHeading(
                    title = "Out with karigars",
                    hint = "Pieces that have left the building.",
                )
            }
            if (outWithKarigars.isEmpty()) {
                item(key = "out-empty") { NothingOutYet() }
            } else {
                items(outWithKarigars.size, key = { "out-" + outWithKarigars[it].karigar }) { i ->
                    OutLoadRow(outWithKarigars[i])
                }
            }
        }
    }
}

// #region Pieces

/**
 * The empty state, which exists to teach rather than to apologise.
 *
 * It names the exact path to the thing, because the feature has no other entrance: a reader
 * who does not already know about "Send out" cannot find it from here by exploring.
 */
@Composable
internal fun NothingOutYet() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Nothing is out at the moment.",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            "When you send pieces to a karigar for printing, dyeing or any other work, they " +
                "come off the shelf and appear here - with how many, who has them and how " +
                "long they have been gone.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            "To send some out: open a design, tap a size, then Send out.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * One karigar's outstanding work.
 *
 * Ordered by the longest wait rather than the biggest pile, because fifty pieces sent
 * yesterday are fine and six sent a month ago are a phone call.
 */
@Composable
internal fun OutLoadRow(load: KarigarLoad) {
    val scheme = MaterialTheme.colorScheme
    val late = load.oldestDaysOut >= JOB_OVERDUE_DAYS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (late) scheme.errorContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KarigarIcon(
            modifier = Modifier.size(18.dp),
            tint = if (late) scheme.onErrorContainer else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                load.karigar,
                style = MaterialTheme.typography.titleSmall,
                color = if (late) scheme.onErrorContainer else scheme.onSurface,
            )
            Text(
                buildString {
                    append(if (load.jobs == 1) "1 lot" else "${load.jobs} lots")
                    append(" - longest ")
                    append(
                        when (load.oldestDaysOut) {
                            0 -> "sent today"
                            1 -> "1 day"
                            else -> "${load.oldestDaysOut} days"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (late) scheme.onErrorContainer else scheme.onSurfaceVariant,
            )
        }
        Text(
            "${load.pieces}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (late) scheme.onErrorContainer else scheme.onSurface,
        )
    }
}
// #endregion
