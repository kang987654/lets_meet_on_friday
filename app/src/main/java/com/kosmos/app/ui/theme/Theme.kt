package com.kosmos.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/**
 * Material3 컴포넌트가 읽는 색 역할을 [KosmosColors] 로만 채운다.
 *
 * [WHY] 비워 둔 역할(surfaceContainer* 등)은 M3 기본 보라 계열로 채워져 시트·메뉴·대화상자가 앱 팔레트와 어긋났다.
 * 하늘색 면 위 글자는 흰색이 아니라 [KosmosColors.onBrand] — 흰 글자는 대비 미달이다(DESIGN.md v3).
 */
private fun schemeOf(colors: KosmosColors): ColorScheme {
    val base = if (colors.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = colors.brand,
        onPrimary = colors.onBrand,
        primaryContainer = colors.brandContainer,
        onPrimaryContainer = colors.textPrimary,
        inversePrimary = colors.brandText,
        secondary = colors.brandText,
        onSecondary = colors.surface,
        secondaryContainer = colors.brandContainer,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.successText,
        onTertiary = colors.surface,
        tertiaryContainer = colors.surfaceSunk,
        onTertiaryContainer = colors.textPrimary,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceSunk,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.surface,
        surfaceBright = colors.surface,
        surfaceDim = colors.surfaceSunk,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surfaceSunk,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.surface,
        error = colors.dangerText,
        onError = colors.surface,
        errorContainer = colors.surfaceSunk,
        onErrorContainer = colors.dangerText,
        outline = colors.border,
        outlineVariant = colors.border,
        scrim = colors.scrim,
    )
}

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
    val colorScheme = schemeOf(kosmosColors)

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

/** 테마 토큰 접근자 — `KosmosTheme.colors.brand` 형태로 사용합니다. */
object KosmosTheme {
    val colors: KosmosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKosmosColors.current
}
