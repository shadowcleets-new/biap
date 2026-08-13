package com.sc.biap.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @file AuthPieces.kt
 * @description Shared parts of the sign-in flow, from the Material 3 Expressive spec.
 *
 * All three sign-in screens use the same field, the same error line and the same primary
 * action, so they live here once. Every colour is a Material ROLE - the palette is
 * generated from the device wallpaper at runtime, so a literal written here would be
 * wrong on every phone but one.
 */

/** Field height and radius are from the spec: filled containers, not outlined boxes. */
private val FieldHeight = 76.dp
val AuthFieldRadius = 24.dp
private val ActionHeight = 64.dp

/**
 * Beyond this the layout stops stretching and centres instead.
 *
 * A sign-in form spread across the full width of a tablet is unreadable - the eye has to
 * travel the whole screen for a two-field form.
 */
private val ContentMaxWidth = 560.dp

/**
 * The frame every sign-in screen sits in.
 *
 * THREE PROBLEMS IT SOLVES, ALL OF THEM SIZE PROBLEMS:
 *
 *  1. INSETS. The app draws edge to edge, so without this the first element sits under the
 *     status bar and the back button is physically unreachable. safeDrawingPadding keeps
 *     content clear of the status bar, the gesture bar and any camera cutout, whatever
 *     shape the device is. It also covers the keyboard, so an open keyboard shrinks the
 *     viewport rather than hiding the button behind it.
 *
 *  2. SHORT SCREENS. Content taller than the display scrolls instead of being cut off. On
 *     a 5.7" phone the code screen would otherwise lose its action button off the bottom.
 *
 *  3. TALL SCREENS. [actions] stay pinned to the bottom rather than floating in the middle
 *     of a 6.8" display, so the same screen reads the same way on every device.
 *
 * The mechanism is a scrollable column with a minimum height of one viewport: when content
 * is short there is spare height and SpaceBetween pushes the actions down; when content is
 * tall there is none and the column simply scrolls.
 */
@Composable
fun AuthScaffold(
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column { content() }
            if (actions != null) Column { actions() }
        }
    }
}

@Composable
fun AuthBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

/** The big screen title. displayLarge is the point - type is the hero in this language. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthTitle(title: String, supporting: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-1.5).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A filled field, not an outlined one.
 *
 * Expressive fills its inputs from the surface container roles and drops the border
 * entirely; an outline here would read as the old language regardless of the radius.
 *
 * BUILT FROM PARTS RATHER THAN USING TextField:
 *   Material's filled TextField reserves a band at the top for its floating label and sizes
 *   the value at bodyLarge. Dropped into a fixed-height container that leaves the value
 *   sitting high in the pill and looking small - which is exactly how it looked against the
 *   design, where the value is the hero of the field and the caption sits quietly above it.
 *
 *   Since the label here never floats (it is always a caption, never a placeholder) there is
 *   nothing to animate, so a caption stacked over a BasicTextField gives the design directly
 *   and centres the pair in the pill. The value keeps a heading-weight style so the number
 *   stays the thing the eye lands on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    // Full width unless a caller needs otherwise - the country-code field sits in a Row and
    // asks for a fixed width, so this cannot be forced inside.
    modifier: Modifier = Modifier.fillMaxWidth(),
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(FieldHeight)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(AuthFieldRadius))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) scheme.error else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = MaterialTheme.typography.titleLargeEmphasized.copy(
                    color = scheme.onSurface,
                ),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.size(8.dp))
            trailingIcon()
        }
    }
}

/**
 * Errors sit in a filled errorContainer pill rather than as red text under a field.
 *
 * A tonal container is readable at a glance and survives being surrounded by other
 * containers, which loose red text on a coloured surface does not.
 */
@Composable
fun AuthErrorLine(message: String, modifier: Modifier = Modifier) {
    if (message.isBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(AuthFieldRadius),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * The primary action: a full-width stadium that shows its own progress.
 *
 * The spinner replaces the label rather than sitting beside it, so the button never
 * changes width mid-press.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthPrimaryAction(
    label: String,
    loadingLabel: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shapes = ButtonDefaults.shapes(shape = CircleShape),
        modifier = modifier
            .fillMaxWidth()
            .height(ActionHeight),
    ) {
        if (isLoading) {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(12.dp))
            Text(loadingLabel, style = MaterialTheme.typography.titleMedium)
        } else {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(12.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A tonal information card, used for the Google consent list.
 *
 * [highlighted] lifts one row into tertiaryContainer - the consequential grant deserves
 * to look different from the reassuring ones.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthInfoCard(
    title: String,
    body: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (highlighted) scheme.tertiaryContainer else scheme.surfaceContainerHigh
    val onContainer = if (highlighted) scheme.onTertiaryContainer else scheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = onContainer)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer.copy(alpha = 0.8f),
        )
    }
}

/** One OTP digit cell. Filled when it holds a digit, outlined-empty when it does not. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpCell(digit: Char?, isNext: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val filled = digit != null
    Box(
        modifier = modifier
            .height(78.dp)
            .background(
                if (filled) scheme.primaryContainer else scheme.surfaceContainerHigh,
                RoundedCornerShape(22.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit?.toString() ?: if (isNext) "|" else "",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = if (filled) scheme.onPrimaryContainer else scheme.outline,
        )
    }
}
