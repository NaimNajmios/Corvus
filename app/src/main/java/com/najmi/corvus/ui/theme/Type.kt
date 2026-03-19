package com.najmi.corvus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.najmi.corvus.R

val DmSerifDisplay = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal)
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium)
)

val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.02.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DmSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.005.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.005.sp
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.005.sp
    )
)
