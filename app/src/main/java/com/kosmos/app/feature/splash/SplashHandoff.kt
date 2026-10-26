package com.kosmos.app.feature.splash

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import com.kosmos.app.ui.theme.SplashStyle

/**
 * [SplashHandoff]
 * 스플래시 장면을 채팅 위로 이어 그리는 전환 덮개의 상태(0.38.4 M6).
 *
 * ### Architecture Context
 * - **Layer**: UI (셸) — `MainScreen` 이 하나 만들어 [LocalSplashHandoff] 로 내린다.
 * - 스플래시는 Ready 에서 [begin] 하고 채팅으로 바로 이동한다. 덮개([SplashOverlay])가 같은 시계로 장면을 이어 그리며 진짜 채팅을 드러낸다.
 *
 * ### Key Flow
 * 1. 채팅 요소가 [splashAnchor] 로 자기 자리를 알린다(전환이 끝나면 더 받지 않는다).
 * 2. 덮개가 자리를 읽어 빛이 박힐 곳·별이 앉을 곳을 정한다.
 * 3. 은하 장면은 [chatScale]·[chatOrigin] 으로 채팅을 줌인시킨다 — 셸이 그리기 단계에서만 읽는다.
 *
 * [WHY] 스플래시를 내비게이션 첫 화면으로 둔 채(오류·모델 없음 안내) 전환만 셸 층에서 한다 — 채팅이 덮개 아래에서 먼저
 * 그려져 있어야 드러낼 수 있고, 채팅을 처음 그리는 무게가 장면이 멈춰 있는 동안에 지나간다.
 */
@Stable
class SplashHandoff {
    /** 전환 중이면 그 정보, 아니면 null. */
    var exit by mutableStateOf<SplashExit?>(null)
        private set

    var chatScale by mutableFloatStateOf(1f)
    var chatOrigin by mutableStateOf(Offset.Unspecified)

    private val anchors = HashMap<Any, Pair<SplashAnchor, Rect>>()
    private var collecting = true

    /** 이미 전환 중이면 무시한다 — Ready 가 재진입 요동으로 두 번 올 수 있다. */
    fun begin(exit: SplashExit) {
        if (this.exit == null && collecting) this.exit = exit
    }

    fun finish() {
        exit = null
        collecting = false
        chatScale = 1f
        chatOrigin = Offset.Unspecified
        anchors.clear()
    }

    /**
     * 자리 받기를 멈춘다 — 은하 전환이 채팅을 키우면 보이는 자리가 따라 바뀌므로, 읽은 순간의 자리로 고정한다.
     */
    fun stopCollecting() {
        collecting = false
    }

    internal fun report(owner: Any, kind: SplashAnchor, bounds: Rect) {
        if (collecting) anchors[owner] = kind to bounds
    }

    internal fun forget(owner: Any) {
        anchors.remove(owner)
    }

    /** 지금 화면 안에 보이는 [kind] 자리들. */
    fun anchorsOf(kind: SplashAnchor, screen: Rect): List<Rect> =
        anchors.values.filter { it.first == kind && it.second.overlaps(screen) && it.second.width > 0f }.map { it.second }
}

/**
 * 전환 하나 — 스플래시가 돌리던 장면 시계([clock])와 Ready 를 본 장면 시각([readyAt]), 그때의 안내 문구.
 */
data class SplashExit(val style: SplashStyle, val clock: SceneClock, val readyAt: Long, val status: String)

/**
 * 장면 시계 — 프레임 사이 간격을 [MAX_STEP_MS] 로 잘라 더한다. 보통 프레임과 전환 중 무거운 프레임(~120ms)은 그대로라 장면 속도는 벽시계와 같다.
 *
 * [WHY] 엔진이 GPU 에 가중치를 올리는 동안 화면이 2~3초 멈춘다(앱 코드가 아니라 GPU 가 꽉 참, device_gate R 메모). 벽시계 그대로면
 * 멈춤이 풀리는 순간 장면이 멈춘 시간만큼 건너뛰어 "툭" 끊긴다 — 멈춘 자세에서 이어지게 한다. 스플래시 → 덮개로 넘어가는 사이의
 * 채팅 첫 그리기 틈도 같은 이유로 건너뛰지 않는다.
 */
class SceneClock {
    private var lastFrame = -1L

    /** 지금까지 흐른 장면 시각(ms). */
    var time = 0L
        private set

    /** 프레임 시각을 받아 장면 시각을 진행하고 돌려준다. */
    fun tick(frameMillis: Long): Long {
        if (lastFrame >= 0) time += (frameMillis - lastFrame).coerceIn(0L, MAX_STEP_MS)
        lastFrame = frameMillis
        return time
    }

    companion object {
        // [WHY] 50 이면 전환 중 100ms 남짓한 프레임까지 잘려 전환이 늘어나고 느린 프레임이 더 많아졌다(실기기 framestats, 0.38.6 M6) — 몇 초짜리 멈춤만 자른다.
        const val MAX_STEP_MS = 150L
    }
}

/** 채팅이 알리는 자리 종류. */
enum class SplashAnchor { HEADER_STAR, TITLE, INPUT_BAR, BUBBLE, EMPTY }

val LocalSplashHandoff = staticCompositionLocalOf<SplashHandoff?> { null }

/**
 * 시작 화면 전환이 이 요소 자리를 알 수 있게 한다. 셸 밖(E2E 단독 compose·카탈로그)에서는 아무것도 하지 않는다.
 */
fun Modifier.splashAnchor(kind: SplashAnchor): Modifier = this then SplashAnchorElement(kind)

private data class SplashAnchorElement(val kind: SplashAnchor) : ModifierNodeElement<SplashAnchorNode>() {
    override fun create() = SplashAnchorNode(kind)
    override fun update(node: SplashAnchorNode) {
        node.kind = kind
    }
}

private class SplashAnchorNode(var kind: SplashAnchor) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var handoff: SplashHandoff? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val h = handoff ?: currentValueOf(LocalSplashHandoff).also { handoff = it } ?: return
        h.report(this, kind, coordinates.boundsInRoot())
    }

    override fun onDetach() {
        handoff?.forget(this)
        handoff = null
    }
}
