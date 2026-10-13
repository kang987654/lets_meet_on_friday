package com.kosmos.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * [ThemeMode]
 * 사용자가 설정 화면에서 선택하는 테마 모드입니다. DataStore에는 [key] 문자열로 영속됩니다.
 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("SYSTEM", "시스템 설정"),
    LIGHT("LIGHT", "라이트"),
    DARK("DARK", "다크");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private fun darkSchemeOf(colors: KosmosColors) = darkColorScheme(
    primary = colors.accent,
    secondary = colors.accentAlt,
    tertiary = colors.success,
    background = colors.bg,
    surface = colors.surface,
    error = colors.danger,
    onPrimary = colors.bg,
    onSecondary = colors.bg,
    onTertiary = colors.bg,
    onBackground = colors.textPrimary,
    onSurface = colors.textPrimary,
)

private fun lightSchemeOf(colors: KosmosColors) = lightColorScheme(
    primary = colors.accent,
    secondary = colors.accentAlt,
    tertiary = colors.success,
    background = colors.bg,
    surface = colors.surface,
    error = colors.danger,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = colors.textPrimary,
    onSurface = colors.textPrimary,
)

/**
 * [KosmosTheme]
 * 앱 전역 테마. 선택된 [ThemeMode]에 따라 라이트/다크 팔레트를 [LocalKosmosColors]로 제공하고
 * Material3 ColorScheme·상태바 외관을 함께 동기화합니다. (ADR-005)
 *
 * 화면에서는 `KosmosTheme.colors.<토큰>`으로 색상에 접근합니다.
 */
@Composable
fun KosmosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val kosmosColors = if (useDark) DarkColors else LightColors
    val colorScheme = if (useDark) darkSchemeOf(kosmosColors) else lightSchemeOf(kosmosColors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // [WHY] 시스템 바 아이콘은 테마와 무관하게 **밝게** 둔다 — 매니페스트 테마(`Theme.NoTitleBar`, 옛 테마)는 시스템이 바 배경을
            // 직접 검게 그려 앱이 칠할 수 없다(statusBarColor·창 배경 모두 실기기에서 무효, 0.36.1). 라이트 모드에서 어두운 아이콘을 쓰면
            // 검은 바 위에서 사라졌다. 바를 앱 색으로 칠하려면 edge-to-edge 전환(각 화면이 인셋을 직접 처리)이 필요하다.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalKosmosColors provides kosmosColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

/** 테마 토큰 접근자 — `KosmosTheme.colors.accent` 형태로 사용합니다. */
object KosmosTheme {
    val colors: KosmosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKosmosColors.current
}
