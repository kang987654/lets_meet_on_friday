package com.kosmos.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.kosmos.app.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WidgetRefresher]
 * 일정·할 일이 바뀐 직후 홈 위젯을 다시 그리게 합니다 (A3).
 *
 * ### Architecture Context
 * - **Layer**: App (Widget)
 * - **Dependencies**: Glance updateAll
 *
 * [WHY] 인터페이스로 분리한다 — 훅을 거는 호출측(툴 실행기·뷰모델)의 단위 테스트를 Glance
 * 에서 떼기 위함 (Notifier 전례). TaskRepository 가 Flow 를 노출하지 않는 suspend-only
 * 계약이라 위젯이 구독으로 따라갈 수 없어, 쓰기 경로가 명시적으로 이 훅을 건다.
 */
interface WidgetRefresher {
    suspend fun refresh()

    /**
     * 다음 앱 시작 때 한 번 다시 그리라고 표시합니다 — 백업 복원 직후용.
     *
     * [WHY] 복원은 DB 파일을 통째로 바꾸지만 앱이 살아 있는 동안 위젯은 같은 프로세스의 옛 DB 연결로 읽어, 바로 [refresh] 해도 복원 전 내용이 그려졌다.
     * 재시작한 프로세스는 새 DB 를 연다 — 그때 [refreshIfPending] 이 그린다(0.41.14 D2).
     */
    fun refreshOnNextStart() {}

    /** [refreshOnNextStart] 표시가 있으면 한 번 다시 그리고 표시를 지웁니다 — 앱 시작 때. */
    suspend fun refreshIfPending() {}
}

@Singleton
open class GlanceWidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetRefresher {

    override suspend fun refresh() {
        // [WHY] 위젯 갱신 실패가 본 기능(일정·할 일 저장)을 실패시키면 안 된다 — 경고만 남긴다.
        runCatching { KosmosWidget().updateAll(context) }
            .onFailure { AppLogger.w(TAG, "위젯 갱신 실패(기능은 정상): ${it.message}") }
    }

    private val pendingMarker get() = File(context.noBackupFilesDir, PENDING_MARKER)

    override fun refreshOnNextStart() {
        runCatching { pendingMarker.createNewFile() }
            .onFailure { AppLogger.w(TAG, "위젯 갱신 표시 실패: ${it.message}") }
    }

    override suspend fun refreshIfPending() {
        if (!pendingMarker.exists()) return
        pendingMarker.delete()
        refresh()
    }

    private companion object {
        const val TAG = "GlanceWidgetRefresher"
        const val PENDING_MARKER = "widget_refresh_pending"
    }
}
