package com.example.fit.ui.theme

import androidx.compose.ui.graphics.Color

// The app's own fixed colours (spec section 7): the same on every phone, light and dark.

val PrimaryLight = Color(0xFF3C4BC0)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFECEEFB)
val OnPrimaryContainerLight = Color(0xFF1B2470)
val BackgroundLight = Color(0xFFF5F4FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEEDF6)
val OnSurfaceLight = Color(0xFF1C1B1F)
val OnSurfaceVariantLight = Color(0xFF6B6A75)
val OutlineLight = Color(0xFFCFCDD9)
val OutlineVariantLight = Color(0xFFE3E1EC)

val PrimaryDark = Color(0xFFB4BDFF)
val OnPrimaryDark = Color(0xFF1A237E)
val PrimaryContainerDark = Color(0xFF2B3594)
val OnPrimaryContainerDark = Color(0xFFDEE0FF)
val BackgroundDark = Color(0xFF121217)
val SurfaceDark = Color(0xFF1D1C23)
val SurfaceVariantDark = Color(0xFF2A2933)
val OnSurfaceDark = Color(0xFFE6E4EE)
val OnSurfaceVariantDark = Color(0xFFA8A6B3)
val OutlineDark = Color(0xFF474552)
val OutlineVariantDark = Color(0xFF34323D)

val ErrorLight = Color(0xFFB3261E)
val ErrorDark = Color(0xFFF2B8B5)

/** Colours Material's scheme has no slot for: the done tick, the record badge and the charts. */
val LightOverloadColors = OverloadColors(
    done = Color(0xFF2E9E5B),
    recordContainer = Color(0xFFFFF3CD),
    onRecordContainer = Color(0xFF8A5A00),
    chartSurface = Color(0xFFFCFCFB),
    chartSession = Color(0xFF898781),
    chartLoad = Color(0xFF2A78D6),
    chartCardio = Color(0xFFEB6834),
    chartGrid = Color(0xFFE1E0D9),
    chartBaseline = Color(0xFFC3C2B7),
    chartLabel = Color(0xFF898781),
)

val DarkOverloadColors = OverloadColors(
    done = Color(0xFF4CC27F),
    recordContainer = Color(0xFF4A3A00),
    onRecordContainer = Color(0xFFFFD978),
    chartSurface = Color(0xFF1A1A19),
    chartSession = Color(0xFF898781),
    chartLoad = Color(0xFF3987E5),
    chartCardio = Color(0xFFD95926),
    chartGrid = Color(0xFF2C2C2A),
    chartBaseline = Color(0xFF383835),
    chartLabel = Color(0xFF898781),
)
