package com.sc.biap.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sc.biap.ui.BiapMark

/**
 * @file AuthChoiceScreen.kt
 * @description The way in — three routes, deliberately unequal.
 *
 * Redesigned from the Material 3 Expressive spec produced in Lovable.
 *
 * WHAT MAKES IT EXPRESSIVE, RATHER THAN JUST ROUNDER:
 *   The previous version was three identical full-width buttons stacked in a column. Three
 *   equal options give a person no help at all - they have to read every one before
 *   choosing. Expressive says a screen should have ONE saturated moment surrounded by
 *   tonal siblings, so email gets a filled primary panel and the other two share a bento
 *   pair in container tones. The hierarchy does the deciding.
 *
 *   Colour comes entirely from Material roles, never fixed values, because the palette is
 *   generated from the device wallpaper at runtime and any hex written here would be wrong
 *   on every phone but one.
 *
 *   Shapes come from MaterialShapes rather than rounded rectangles - Sunny behind the
 *   mark, Flower behind the phone glyph. That shape vocabulary is a large part of what
 *   makes the language recognisable.
 */

// Spec geometry from the design. Intrinsic sizes, so they stay literal rather than being
// forced onto the spacing scale.
private val IdentityBadge = 88.dp
private val MarkInBadge = 44.dp
private val HeroHeight = 116.dp
private val HeroRadius = 36.dp
private val HeroChevron = 56.dp
private val TileHeight = 176.dp
private val TileRadius = 32.dp
private val TileGlyph = 52.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthChoiceScreen(
    onEmailSelected: () -> Unit,
    onPhoneSelected: () -> Unit,
    onGoogleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 32.dp)
    ) {
        // --- identity ------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(IdentityBadge)
                    .clip(MaterialShapes.Sunny.toShape())
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                BiapMark(
                    size = MarkInBadge,
                    color = scheme.onPrimaryContainer,
                    showNodes = false,
                )
            }

            Spacer(Modifier.size(20.dp))

            Column {
                Text(
                    text = "BIAP",
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = scheme.onSurface,
                    letterSpacing = (-1.5).sp,
                )
                Text(
                    text = "Atelier operations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Sign in to\nthe workshop.",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = scheme.onSurface,
        )

        Spacer(Modifier.height(28.dp))

        // --- the one saturated moment --------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .clip(RoundedCornerShape(HeroRadius))
                .background(scheme.primary)
                .clickable(onClick = onEmailSelected)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RECOMMENDED",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimary.copy(alpha = 0.8f),
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = "Sign in with Email",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = scheme.onPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .size(HeroChevron)
                    .clip(CircleShape)
                    .background(scheme.onPrimary.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- tonal siblings ------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceTile(
                title = "Google",
                supporting = "Also enables Drive backup",
                container = scheme.secondaryContainer,
                onContainer = scheme.onSecondaryContainer,
                glyphBackground = scheme.surfaceContainerLowest,
                onClick = onGoogleSelected,
                modifier = Modifier.weight(1f),
            ) {
                // Google's mark is a fixed brand asset and is the one thing on this screen
                // that must NOT take a theme colour.
                GoogleGlyph()
            }

            ChoiceTile(
                title = "Phone",
                supporting = "Sign in with an OTP",
                container = scheme.tertiaryContainer,
                onContainer = scheme.onTertiaryContainer,
                glyphBackground = scheme.surfaceContainerLowest,
                glyphShape = MaterialShapes.Flower.toShape(),
                onClick = onPhoneSelected,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceTile(
    title: String,
    supporting: String,
    container: Color,
    onContainer: Color,
    glyphBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphShape: androidx.compose.ui.graphics.Shape = CircleShape,
    glyph: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .height(TileHeight)
            .clip(RoundedCornerShape(TileRadius))
            .background(container)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(TileGlyph)
                .clip(glyphShape)
                .background(glyphBackground),
            contentAlignment = Alignment.Center,
        ) { glyph() }

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = onContainer,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Start,
            )
        }
    }
}

/**
 * Google's "G" in its own brand colours.
 *
 * Drawn rather than imported so it needs no asset, and left uncoloured by the theme on
 * purpose: Google's brand guidelines require the mark keep its own palette, and it is the
 * single element on this screen exempt from the runtime colour scheme.
 */
@Composable
private fun GoogleGlyph() {
    Text(
        text = "G",
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = Color(0xFF4285F4),
    )
}
