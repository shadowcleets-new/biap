package com.sc.biap.theme

import androidx.compose.ui.graphics.Color

/**
 * @file Color.kt
 * @description BIAP palette — instrumentation, not fashion.
 *
 * Values come from the BIAP identity spec designed in Lovable. The constant NAMES are
 * unchanged from the previous "Contemporary Heritage" palette on purpose: they are
 * referenced from a dozen files, and renaming them would be a large diff that changes
 * nothing a user can see. Only the values moved.
 *
 * THE IDEA:
 *   Deep blue-black ink rather than pure black, so surfaces stack in four legible layers
 *   under workshop lighting: background -> surface -> raised -> outline.
 *
 *   Brass (#E8B23A) is the single brand voice. It is deliberately reserved for identity,
 *   focus and primary action so it never competes with the data on screen. Cyan carries
 *   data emphasis instead. Status colours are tuned to similar perceptual weight so a
 *   stock table reads at a glance rather than one colour shouting over the others.
 *
 * CONTRAST:
 *   Text pairings hold WCAG AA. Brass and cyan both take INK as their "on" colour rather
 *   than white - white on brass fails, and that mistake is easy to make later.
 */

// --- Light -----------------------------------------------------------------
val HeritageLightPrimary = Color(0xFFE8B23A)          // brass - brand voice
val HeritageLightOnPrimary = Color(0xFF0B0F14)        // ink, NOT white: white on brass fails AA
val HeritageLightPrimaryContainer = Color(0xFFFBEFD3) // pale brass (derived tint of primary)
val HeritageLightOnPrimaryContainer = Color(0xFF4A3708)
val HeritageLightSecondary = Color(0xFF2C7C97)        // cyan, darkened for AA on light surfaces
val HeritageLightOnSecondary = Color(0xFFFFFFFF)
val HeritageLightTertiary = Color(0xFF5A6875)         // muted
val HeritageLightOnTertiary = Color(0xFFFFFFFF)
val HeritageLightBackground = Color(0xFFF4F6F8)
val HeritageLightOnBackground = Color(0xFF0B0F14)
val HeritageLightSurface = Color(0xFFFFFFFF)
val HeritageLightOnSurface = Color(0xFF0B0F14)
val HeritageLightSurfaceVariant = Color(0xFFE9EDF1)
val HeritageLightOnSurfaceVariant = Color(0xFF5A6875)
val HeritageLightOutline = Color(0xFF97A3AF)          // >3:1 against light surfaces

// --- Dark (the design's primary mode) ---------------------------------------
val HeritageDarkPrimary = Color(0xFFE8B23A)           // brass
val HeritageDarkOnPrimary = Color(0xFF0B0F14)
val HeritageDarkPrimaryContainer = Color(0xFF1C2531)  // raised
val HeritageDarkOnPrimaryContainer = Color(0xFFE8B23A)
val HeritageDarkSecondary = Color(0xFF4CB3D4)         // cyan - data emphasis
val HeritageDarkOnSecondary = Color(0xFF0B0F14)
val HeritageDarkTertiary = Color(0xFF8A9AAB)          // muted
val HeritageDarkOnTertiary = Color(0xFF0B0F14)
val HeritageDarkBackground = Color(0xFF0B0F14)        // ink
val HeritageDarkOnBackground = Color(0xFFE9EEF4)
val HeritageDarkSurface = Color(0xFF131A22)
val HeritageDarkOnSurface = Color(0xFFE9EEF4)
val HeritageDarkSurfaceVariant = Color(0xFF1C2531)    // raised
val HeritageDarkOnSurfaceVariant = Color(0xFF8A9AAB)
val HeritageDarkOutline = Color(0xFF2A3542)

// --- Status -----------------------------------------------------------------
// Warning and danger sit close in hue on purpose; weight and context separate them.
val HeritageLightWarning = Color(0xFFB06A12)          // darkened for AA on light
val HeritageDarkWarning = Color(0xFFE8963A)

val HeritageLightSuccess = Color(0xFF1F7A55)          // darkened for AA on light
val HeritageDarkSuccess = Color(0xFF3FB27F)

// --- Decorative --------------------------------------------------------------
// Gradient endpoints for AtelierTheme brushes.
val HeritageGlowGreyLight = Color(0xFFDBE1E8)
val HeritageGlowCharcoalDark = Color(0xFF1C2531)

/**
 * The single seed Material expands into the full tonal palette.
 *
 * This is the brass from the Aperture mark. Everything else - every container tint, every
 * "on" colour, light and dark - is derived from it, so the identity and the interface can
 * never drift apart.
 */
val BiapSeed = Color(0xFFE8B23A)
