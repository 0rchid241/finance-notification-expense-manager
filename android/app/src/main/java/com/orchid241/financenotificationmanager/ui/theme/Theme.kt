package com.orchid241.financenotificationmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = FinancePrimaryLight,
    onPrimary = FinanceSurfaceLight,
    primaryContainer = ColorTokens.PrimaryContainerLight,
    onPrimaryContainer = FinanceTextPrimaryLight,
    secondary = FinanceTextSecondaryLight,
    onSecondary = FinanceSurfaceLight,
    background = FinanceBackgroundLight,
    onBackground = FinanceTextPrimaryLight,
    surface = FinanceSurfaceLight,
    onSurface = FinanceTextPrimaryLight,
    surfaceVariant = FinanceSurfaceSecondaryLight,
    onSurfaceVariant = FinanceTextSecondaryLight,
    outline = FinanceDividerLight,
    outlineVariant = FinanceDividerLight,
    error = FinanceStatusColors.Outflow,
    onError = FinanceSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = FinancePrimaryDark,
    onPrimary = FinanceTextPrimaryDark,
    primaryContainer = ColorTokens.PrimaryContainerDark,
    onPrimaryContainer = FinanceTextPrimaryDark,
    secondary = FinanceTextSecondaryDark,
    onSecondary = FinanceBackgroundDark,
    background = FinanceBackgroundDark,
    onBackground = FinanceTextPrimaryDark,
    surface = FinanceSurfaceDark,
    onSurface = FinanceTextPrimaryDark,
    surfaceVariant = FinanceSurfaceSecondaryDark,
    onSurfaceVariant = FinanceTextSecondaryDark,
    outline = FinanceDividerDark,
    outlineVariant = FinanceDividerDark,
    error = FinanceStatusColors.Outflow,
    onError = FinanceTextPrimaryDark,
)

private object ColorTokens {
    val PrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFFEAF3FF)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF173253)
}

@Composable
fun FinanceNotificationManagerTheme(
    darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = FinanceTypography,
        content = content,
    )
}
