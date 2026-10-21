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
 * [ChatTextSize]
 * 설정에서 고르는 **채팅 메시지** 글자 크기 — 본문 단계(기본 16)를 기준으로 채팅 목록 안의 글자 단계를 같은 비율로 키운다(0.38.2, 사용자 결정: 채팅만·4단계).
 * 상단 바·요약 타일·입력바·다른 화면은 그대로다(시스템 글자 크기는 그 위에 곱해진다).
 */
enum class ChatTextSize(val key: String, val label: String, val bodySp: Int) {
    SMALL("SMALL", "작게", 14),
    DEFAULT("DEFAULT", "기본", 16),
    LARGE("LARGE", "크게", 18),
    XLARGE("XLARGE", "아주 크게", 20);

    /** 본문 단계(16) 대비 배율. */
    val scale: Float get() = bodySp / 16f

    companion object {
        fun fromKey(key: String?): ChatTextSize = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** 채팅 목록이 읽는 글자 배율 — [KosmosTheme] 가 [ChatTextSize] 로 채운다. */
val LocalChatTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/**
 * [FontMode]
 * 설정에서 고르는 글꼴 — 앱 글꼴(하다 라운드)이나 기기에서 고른 글꼴. 크기·굵기 단계는 같다(DESIGN.md v3 Typography).
 */
enum class FontMode(val key: String, val label: String) {
    APP("APP", "앱 글꼴"),
    SYSTEM("SYSTEM", "시스템 글꼴");

    companion object {
        fun fromKey(key: String?): FontMode = entries.firstOrNull { it.key == key } ?: APP
    }
}

/**
 * [SplashStyle]
 * 설정에서 고르는 시작 화면 장면(0.38.4 M6, 사용자 결정 — 두 안 모두 확정, 기본 행성).
 */
enum class SplashStyle(val key: String, val label: String) {
    PLANET("PLANET", "행성"),
    GALAXY("GALAXY", "은하");

    companion object {
        fun fromKey(key: String?): SplashStyle = entries.firstOrNull { it.key == key } ?: PLANET
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
        // [WHY] 스낵바 액션(되돌리기)은 반전 면(inverseSurface) 위 글자다 — 같은 테마의 brandText 는 다크에서 밝은 면 위 밝은 하늘(약 2:1)이었다(0.38.3 M6 점검 P1).
        // 반대 테마의 brandText 가 반전 면 위 대비를 갖는다.
        inversePrimary = if (colors.isLight) DarkColors.brandText else LightColors.brandText,
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
 * Material3 ColorScheme·상태바 외관을 함께 동기화합니다. (ADR-005) [FontMode] 에 따라 글자 단계의 글꼴(하다 라운드/기기 글꼴)을 고릅니다.
 *
 * 화면에서는 `KosmosTheme.colors.<토큰>`으로 색상에 접근합니다.
 */
@Composable
fun KosmosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontMode: FontMode = FontMode.APP,
    chatTextSize: ChatTextSize = ChatTextSize.DEFAULT,
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
            // [WHY] 바는 투명(enableEdgeToEdge)이라 앱 바탕이 비친다 — 아이콘 밝기는 시스템이 아니라 **앱 테마**를 따라야
            // 라이트 바탕 위 흰 아이콘·다크 바탕 위 검은 아이콘이 생기지 않는다(앱 테마는 시스템 설정과 다를 수 있다).
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = kosmosColors.isLight
                isAppearanceLightNavigationBars = kosmosColors.isLight
            }
            // [WHY] 3버튼 내비게이션에서 시스템이 반투명 막을 덧칠하지 않게 — 바 색은 앱 바탕 한 가지.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalKosmosColors provides kosmosColors, LocalChatTextScale provides chatTextSize.scale) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (fontMode == FontMode.APP) AppTypography else SystemTypography,
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
