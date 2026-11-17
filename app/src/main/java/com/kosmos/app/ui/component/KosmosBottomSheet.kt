package com.kosmos.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.kosmos.app.ui.theme.KosmosTheme
import kotlinx.coroutines.launch

/**
 * 앱의 아래 시트 — M3 [ModalBottomSheet] 에 뒤로가기를 직접 단다.
 *
 * [WHY] M3 시트의 예측 뒤로가기는 시트를 줄이며 닫고(사용자 결정: 축소 움직임 빼기), 휴대폰(삼성 3버튼)에서 첫 뒤로가기에 닫히지 않았다 —
 * 에뮬레이터(키·제스처·3버튼)에서는 재현되지 않음(0.42.2 M2). 예측 콜백을 끄고(`shouldDismissOnBackPress = false`) 일반 [BackHandler] 로
 * 아래로 내리며 닫는다 — 끌기·바깥 누르기 닫힘은 그대로.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KosmosBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = KosmosTheme.colors.surface,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler {
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }
        content()
    }
}
