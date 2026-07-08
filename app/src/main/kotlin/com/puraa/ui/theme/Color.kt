package com.puraa.ui.theme

import androidx.compose.ui.graphics.Color

// Brand accent — teal. A bright tone with dark text for the dark theme,
// a deeper saturated tone with white text for light, so it stays a
// confident accent on either ground.
//
// The SATURATED accent is reserved for the one primary action per screen
// (the filled "Save" button) and true selection. Everything else that wants
// to feel "teal" — an emphasised stat, a picked toggle segment — uses the
// muted *container* tone below, so the loud fill only ever means "the action".
val AccentDark = Color(0xFF45D6C0)
val OnAccentDark = Color(0xFF04211D)
val AccentLight = Color(0xFF0C7D6C)
val OnAccentLight = Color(0xFFFFFFFF)

// Tonal accent — a soft, low-chroma teal that sits between surface and the
// saturated accent. Used for accent emphasis that is NOT the primary action.
val AccentContainerDark = Color(0xFF173B34)
val OnAccentContainerDark = Color(0xFF8FEADA)
val AccentContainerLight = Color(0xFFC7EAE2)
val OnAccentContainerLight = Color(0xFF083F37)

// Dark theme — near-black with a faint cool bias toward the teal accent.
val DarkBackground = Color(0xFF0B0E0D)
val DarkSurface = Color(0xFF161A19)
val DarkSurfaceVariant = Color(0xFF232928)
val DarkOnSurface = Color(0xFFEFF3F1)
val DarkOnSurfaceVariant = Color(0xFF98A09D)
val DarkOutline = Color(0xFF373E3C)

// Light theme — cool off-white with clean white cards.
val LightBackground = Color(0xFFF7FAF8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EEEB)
val LightOnSurface = Color(0xFF121615)
val LightOnSurfaceVariant = Color(0xFF566060)
val LightOutline = Color(0xFFD2D9D6)

val SoftRed = Color(0xFFE5484D)
