package com.kosmos.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.theme.KosmosTheme

@Composable
fun OrbPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Orb")
    
    val ringSpin1 by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ringSpin1"
    )
    
    val ringSpin2 by transition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "ringSpin2"
    )
    
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // [WHY] DrawScope는 @Composable이 아니므로 테마 토큰을 미리 읽어둔다.
    val accent = KosmosTheme.colors.accent
    val accentAlt = KosmosTheme.colors.accentAlt

    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            
            // Ring 3
            drawCircle(color = accent.copy(alpha = 0.08f), radius = 72.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            
            // Ring 2
            translate(left = centerOffset.x, top = centerOffset.y) {
                rotate(ringSpin2, pivot = Offset.Zero) {
                    drawCircle(color = accentAlt.copy(alpha = 0.2f), radius = 52.dp.toPx(), center = Offset(0f, 0f), style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                    drawCircle(color = accentAlt, radius = 4.dp.toPx(), center = Offset(0f, 52.dp.toPx()))
                }
            }
            
            // Ring 1
            translate(left = centerOffset.x, top = centerOffset.y) {
                rotate(ringSpin1, pivot = Offset.Zero) {
                    drawCircle(color = accent.copy(alpha = 0.2f), radius = 32.dp.toPx(), center = Offset(0f, 0f), style = Stroke(width = 1.dp.toPx()))
                    drawCircle(color = accent, radius = 6.dp.toPx(), center = Offset(0f, -32.dp.toPx()))
                }
            }
        }
        
        Box(modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
        ) {
            Canvas(modifier = Modifier.size(96.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.4f), accentAlt.copy(alpha = 0.6f)),
                        center = Offset(size.width * 0.35f, size.height * 0.35f),
                        radius = size.width
                    )
                )
            }
        }
    }
}
