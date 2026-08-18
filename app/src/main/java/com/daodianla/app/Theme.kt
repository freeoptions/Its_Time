package com.daodianla.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

object DaoDianLaColors {
    val background = Color(0xFFF1F2FF)
    val ink = Color(0xFF1D2A50)
    val inkSoft = Color(0xFF40548B)
    val blue = Color(0xFF4868AE)
    val blueTint = Color(0xFFDDE5FF)
    val amber = Color(0xFFB9C8FF)
    val muted = Color(0xFF667399)
    val line = Color(0xFFE3E6F5)
    val disabled = Color(0xFFF0F1F8)
    val hero = Color(0xFFB7C8FA)
    val warningTint = Color(0xFFFFF1D2)
    val warning = Color(0xFF94651C)
}

private val LightColors = lightColorScheme(
    primary = DaoDianLaColors.blue,
    onPrimary = Color.White,
    secondary = DaoDianLaColors.amber,
    onSecondary = DaoDianLaColors.ink,
    background = DaoDianLaColors.background,
    onBackground = DaoDianLaColors.ink,
    surface = Color.White,
    onSurface = DaoDianLaColors.ink,
    outline = DaoDianLaColors.line
)

private val DaoDianLaTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

private val DaoDianLaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DaoDianLaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = DaoDianLaTypography,
        shapes = DaoDianLaShapes,
        content = content
    )
}
