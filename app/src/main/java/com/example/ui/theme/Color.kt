package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Literary & Executive Palette (Clean Royal Blue, Midnight Slate)
val LuminaAccentPrimary = Color(0xFF2563EB)       // Vibrant Royal Blue
val LuminaAccentDark = Color(0xFF1D4ED8)          // Deep Sapphire Blue
val LuminaAccentLight = Color(0xFF3B82F6)         // Luminous Sky Blue
val LuminaAccentSubtle = Color(0xFFDBEAFE)        // Soft Tint Blue

val DeepSlate = Color(0xFF161F2E)                // Rich Midnight Indigo
val MutedSlate = Color(0xFF5A6679)               // Soft Editorial Slate

// Light Theme Paper & Background Colors
val PaperWhite = Color(0xFFFBF9F5)               // Warm Alabaster Canvas
val PaperCream = Color(0xFFF6F2E9)               // Japanese Linen Paper
val PaperSepia = Color(0xFFEFE6D5)               // Heritage Parchment
val SurfaceWhite = Color(0xFFFFFFFF)             // Pure Surface White
val BorderSubtle = Color(0xFFE8E2D7)             // Warm Outline
val BorderLight = Color(0xFFF0EBE2)

// Dark Theme Colors (Deep Midnight Indigo & Velvet Slate)
val VelvetDark = Color(0xFF0F141C)               // Rich Midnight Ink
val VelvetSurface = Color(0xFF161D28)            // Elevated Midnight Surface
val VelvetCard = Color(0xFF1F2837)               // Refined Slate Card
val VelvetTextPrimary = Color(0xFFF1F5F9)        // Crisp Frosted Pearl
val VelvetTextSecondary = Color(0xFF94A3B8)      // Muted Slate Pearl
val VelvetBorder = Color(0xFF283447)             // Subtle Indigo Border

// Text Hierarchy
val TextPrimary = Color(0xFF181D26)
val TextSecondary = Color(0xFF5A6679)
val TextTertiary = Color(0xFF94A0B2)

// Reader Color Modes
enum class ReaderThemeMode(
    val title: String,
    val background: Color,
    val surface: Color,
    val textColor: Color,
    val textSecondaryColor: Color,
    val accentColor: Color,
    val spineShadowColor: Color,
    val isDark: Boolean
) {
    WHITE(
        title = "Clean White",
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        textColor = Color(0xFF0F172A),
        textSecondaryColor = Color(0xFF475569),
        accentColor = Color(0xFF2563EB),
        spineShadowColor = Color.Transparent,
        isDark = false
    ),
    CREAM(
        title = "Warm Linen",
        background = Color(0xFFF7F3E9),
        surface = Color(0xFFFAF6EE),
        textColor = Color(0xFF26211B),
        textSecondaryColor = Color(0xFF6B6256),
        accentColor = Color(0xFF8C5E2D),
        spineShadowColor = Color(0x22352514),
        isDark = false
    ),
    SEPIA(
        title = "Heritage Sepia",
        background = Color(0xFFEFE5D3),
        surface = Color(0xFFF5EBD9),
        textColor = Color(0xFF2E2419),
        textSecondaryColor = Color(0xFF756550),
        accentColor = Color(0xFF7C4F22),
        spineShadowColor = Color(0x26332110),
        isDark = false
    ),
    NIGHT(
        title = "Velvet Night",
        background = Color(0xFF0F141C),
        surface = Color(0xFF161D28),
        textColor = Color(0xFFE8EDF5),
        textSecondaryColor = Color(0xFF94A3B8),
        accentColor = Color(0xFFE6B774),
        spineShadowColor = Color(0x60000000),
        isDark = true
    )
}
