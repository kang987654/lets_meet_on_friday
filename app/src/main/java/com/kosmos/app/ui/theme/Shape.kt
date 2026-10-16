package com.kosmos.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * [KosmosShapes]
 * 모서리 단계 — `docs/DESIGN.md` v3 `rounded`. 화면에서 `RoundedCornerShape(n.dp)` 를 직접 만들지 않고 이 단계를 쓴다.
 */
object KosmosShapes {
    /** 칩, 작은 배지, 분할 버튼 안쪽 */
    val sm = RoundedCornerShape(8.dp)
    /** 버튼, 입력, 카드, 목록 묶음 */
    val md = RoundedCornerShape(12.dp)
    /** 말풍선 */
    val lg = RoundedCornerShape(18.dp)
    /** 바텀시트 윗모서리 */
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val full = CircleShape
}
