package com.sc.biap.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @file AddPieces.kt
 * @description The capture form's furniture: numbered sections, filled wells, tonal pills.
 *
 * [TABLE OF CONTENTS]
 * 1. LOCAL CONSTANTS & CONFIG
 * 2. THE PROGRESS RAIL
 * 3. A FORM SECTION
 * 4. WELLS
 * 5. PILLS AND THE EMPTY PHOTO SLOT
 *
 * RHYTHM, NOT A WIZARD:
 *   A wizard would be wrong here. Staff add designs out of order - often photographing
 *   first and pricing days later - so forcing a sequence would fight how the work actually
 *   happens. The form stays one scroll and stops being flat in three ways instead: every
 *   section is numbered and carries its own status, the one being worked on steps up a
 *   surface level, and a rail in the top bar counts what is filled.
 *
 * NO OUTLINES ANYWHERE:
 *   Every field is a filled well. Focus is an inset ring rather than a border, because the
 *   whole app avoids strokes - and a dashed 1.5dp edge is reserved, meaning only "empty
 *   slot" or "out of stock". Using it for an ordinary text field would spend the one
 *   signal the catalogue relies on.
 */

// #region 1. Local constants & config

private val SectionRadius = 28.dp
private val WellRadius = 20.dp
private val BadgeSize = 28.dp
private val RailBar = 6.dp
private val PillHeight = 40.dp

// #endregion

// #region 2. The progress rail

/**
 * Five bars for five sections, the current one stretched.
 *
 * Sits beside the only hard requirement (a name), so the rail and the Save button say the
 * same thing in two ways: how much is done, and whether it can be kept.
 */
@Composable
fun StepRail(filled: Int, total: Int = 5, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val done = index < filled
            Box(
                modifier = Modifier
                    .width(if (index == filled - 1) 22.dp else 10.dp)
                    .height(RailBar)
                    .clip(CircleShape)
                    .background(if (done) scheme.primary else scheme.surfaceContainerHighest),
            )
        }
    }
}

// #endregion

// #region 3. A form section

/**
 * One numbered section of the form.
 *
 * The badge carries three states at once: a number while untouched, tertiaryContainer while
 * required and unmet, and a filled primary check once satisfied. [status] on the right says
 * the same thing in words - "0 of 5", "Required", "3 on" - so the section can be read
 * without opening it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FormSection(
    number: Int,
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    done: Boolean = false,
    active: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val badgeContainer = when {
        done -> scheme.primary
        required -> scheme.tertiaryContainer
        else -> scheme.surfaceContainerHighest
    }
    val badgeContent = when {
        done -> scheme.onPrimary
        required -> scheme.onTertiaryContainer
        else -> scheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SectionRadius))
            // Elevation encodes attention: the section being worked on steps up one level.
            // An outline would have said the same thing in the one vocabulary this app
            // reserves for empty and out-of-stock.
            .background(if (active) scheme.surfaceContainerHigh else scheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = badgeContent,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeContent,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                letterSpacing = 1.1.sp,
            )
        }
        content()
    }
}

// #endregion

// #region 4. Wells

/**
 * A filled input well: label above, value below, no border at any point.
 *
 * The placeholder is the same size and weight as a real value, only dimmer. Shrinking it -
 * the usual habit - makes the field jump the moment somebody types, and this form has five
 * of them stacked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FieldWell(
    label: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ring = scheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WellRadius))
            .background(scheme.surfaceContainerLowest)
            .then(
                if (focused) {
                    Modifier.drawBehind {
                        // Inset ring rather than an outline: it sits inside the fill, so the
                        // well never changes size when focus arrives.
                        drawRoundRect(
                            color = ring,
                            cornerRadius = CornerRadius(WellRadius.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) scheme.primary else scheme.onSurfaceVariant,
            letterSpacing = 1.1.sp,
        )
        content()
    }
}

/**
 * A well you can type into. The form's only text input.
 *
 * Built from a BasicTextField rather than Material's filled TextField for the same reason
 * the sign-in fields were: the stock component reserves a band for a floating label and
 * sizes the value at bodyLarge, which is smaller and lighter than the value wants to be
 * here. The label sits above the value permanently instead, so nothing moves when you type.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WellTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }

    FieldWell(label = label, focused = focused, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        // Same size and weight as a real value, only dimmer. Shrinking the
                        // placeholder makes the field jump the instant somebody types.
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMediumEmphasized
                        .copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.invoke()
        }
    }
}

// #endregion

// #region 5. Pills and the empty photo slot

/** A tonal pill. Filled when on, one surface step up when off - never outlined. */
@Composable
fun TonalPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp = PillHeight,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The empty photo slot, and the one place a dashed edge is correct.
 *
 * Dashed 1.5dp outlineVariant means "nothing here yet" throughout this app - it is the same
 * mark an out-of-stock size cell wears. Saying it here and nowhere else in the form is what
 * keeps it meaning something.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyPhotoSlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val outlineVariant = scheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(
                    color = outlineVariant,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialShapes.Flower.toShape())
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Add photos",
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = scheme.onSurface,
        )
        Text(
            "First photo becomes the cover",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

// #endregion
