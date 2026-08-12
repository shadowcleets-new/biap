package com.sc.biap.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Contemporary Heritage typography: Serif (editorial luxury) for display/headline/titleLarge,
// SansSerif for body, labels and dense UI text.
val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// #region Emphasis tokens
/**
 * Emphasised variants of the type scale.
 *
 * Material 3 Expressive ships these as first-class tokens (`titleLargeEmphasized` and
 * friends), but that API is `internal` in material3 1.4.0 stable — see
 * HAIKU_OPERATION_MANUAL.md 4.4. These extensions are the stable-API equivalent: one
 * definition of what "emphasised" means per role, so call sites reference a token
 * instead of bolting `fontWeight = FontWeight.Bold` onto a style ad hoc.
 *
 * If the project later moves to material3 1.5.x, these become one-line aliases to the
 * real Expressive tokens and every call site is already correct.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.headlineSmallStrong: TextStyle
    get() = headlineSmallEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.titleLargeStrong: TextStyle
    get() = titleLargeEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.titleMediumStrong: TextStyle
    get() = titleMediumEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.titleSmallStrong: TextStyle
    get() = titleSmallEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.bodyMediumStrong: TextStyle
    get() = bodyMediumEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.bodySmallStrong: TextStyle
    get() = bodySmallEmphasized

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.labelLargeStrong: TextStyle
    get() = labelLargeEmphasized
// #endregion
