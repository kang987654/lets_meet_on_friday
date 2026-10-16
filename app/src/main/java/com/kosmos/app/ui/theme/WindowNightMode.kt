package com.kosmos.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build

/**
 * 앱 테마 설정을 시스템의 **앱별 야간 모드**에도 알려, 앱을 켜는 첫 순간의 창 바탕(`values-night`)이 앱 설정을 따르게 합니다.
 *
 * [WHY] 창 바탕은 Compose 가 그리기 전 시스템이 칠한다 — 시스템 다크 + 앱 라이트면 켤 때 어두운 바탕이 먼저 보였다(0.38.1 M6 실기기).
 * Android 12(S) 미만에는 앱별 야간 모드가 없어 그대로 둔다(그 기기에서는 창 바탕이 시스템 설정을 따른다).
 * 값이 같으면 시스템이 구성을 바꾸지 않아 Activity 가 다시 만들어지지 않는다 — 저장값을 읽은 뒤에만 부른다(초기값으로 부르면 한 번 뒤집혔다 돌아온다).
 */
fun syncApplicationNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
    uiModeManager.setApplicationNightMode(
        when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
    )
}
