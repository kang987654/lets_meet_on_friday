package com.kosmos.app.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.kosmos.app.R
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 하위 화면 공통 상단 바 — ← 아이콘(20) 버튼 + 제목(`title` 20), 바탕은 앱 바탕(DESIGN.md v3 "시스템 바와 화면 틀").
 *
 * [WHY] ← 와 제목을 같은 20 으로 맞춘다 — 17·24 는 화살표가 글자보다 커 보였다(사용자 결정, 0.38.1 M2 시안 비교).
 *
 * [WHY] M3 `TopAppBar` 를 쓰는 이유는 상태바 인셋을 슬롯 스스로 지기 때문이다 — 평범한 Row 를 상단에 두면 edge-to-edge 에서
 * 상태바 밑에 깔린다(AGENTS §2-③). 글자 "뒤로" 버튼은 쓰지 않는다(0.38.0 감사 P1).
 *
 * @param onBack null 이면 ← 를 그리지 않는다(루트 화면).
 * @param backDescription ← 의 접근성 설명 — 창을 닫는 화면(문서 뷰어)은 "닫기".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KosmosTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backDescription: String = "뒤로",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = KosmosTheme.colors
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    KosmosIcon(R.drawable.ic_ph_arrow_left, contentDescription = backDescription, size = IconSize.inline)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bg,
            scrolledContainerColor = colors.bg,
            titleContentColor = colors.textPrimary,
            navigationIconContentColor = colors.textPrimary,
            actionIconContentColor = colors.textSecondary,
        ),
    )
}
