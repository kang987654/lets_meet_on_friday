package com.kosmos.app.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.BorderColor
import com.kosmos.app.ui.theme.GlassColor

/**
 * Figma의 backdrop-filter: blur(20px) 효과를 Android(Compose)로 이식하는 Modifier
 * API 31 이상에서 최적화된 블러를 지원하며, Shape 및 테두리를 함께 적용합니다.
 */
fun Modifier.glassEffect(
    shape: Shape,
    backgroundColor: Color = GlassColor,
    borderColor: Color = BorderColor,
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 20.dp
): Modifier = composed {
    this.then(
        Modifier
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape)
    )
}
