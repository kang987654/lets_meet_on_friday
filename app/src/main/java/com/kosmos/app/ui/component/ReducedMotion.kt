package com.kosmos.app.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 시스템 "애니메이션 제거"(개발자 옵션·접근성의 애니메이션 배율 0)가 켜져 있는가.
 *
 * [WHY] 화면마다 `Settings.Global` 을 따로 읽고 있었다 — 한 곳에서 읽어 움직임이 있는 부품이 같은 기준을 쓰게 한다(0.38.4 M1, M3 에서 `Motion.kt` 와 묶는다).
 * 화면이 다시 그려질 때 다시 읽지 않는다 — 바꾸면 앱을 다시 열 때 반영된다.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
