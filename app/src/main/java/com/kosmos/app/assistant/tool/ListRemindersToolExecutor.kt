package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.domain.util.IsoDateTimeParser
import javax.inject.Inject

/**
 * [ListRemindersToolExecutor]
 * 모델의 `ListReminders` 툴 콜을 받아 아직 울리지 않은 알림 목록을 시각순으로 돌려주는 실행기입니다 (0.38.5 M5).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [TaskRepository]
 *
 * ### Key Flow
 * 1. 울리기 전·취소 안 됨·미완료 알림을 모두 읽는다([TaskRepository.getActiveReminders]).
 * 2. 시각순으로 "[알림]" 묶음을 만든다 — 시각은 한국어 표기(AGENTS §4-7).
 *
 * [WHY] 알림을 볼 툴이 없어 "몇 시로 해 놨지?"에 기억 검색을 부르고 "기억하고 있지 않아요"라고 답했다(exp56 Q0 되묻기 시각 2/6).
 * 이 툴로 목록 8/8·되묻기 6/6(exp56 L, 0.32.0 exp45b 도 6/6). 선언 비용 때문에 오버헤드 예약을 1,450 으로 올렸다(사용자 결정).
 */
class ListRemindersToolExecutor @Inject constructor(
    private val taskRepository: TaskRepository
) : ToolExecutor {
    override val name: String = ToolNames.LIST_REMINDERS

    override suspend fun execute(args: ToolArguments, sessionId: String): String =
        when (val res = taskRepository.getActiveReminders()) {
            is AppResult.Success -> {
                val lines = res.data
                    .mapNotNull { task -> task.remindAtIso?.let { iso -> Triple(IsoDateTimeParser.toEpochMillis(iso) ?: Long.MAX_VALUE, task.title, iso) } }
                    .sortedBy { it.first }
                    .map { (_, title, iso) -> "- $title (${IsoDateTimeParser.toDisplayKorean(iso) ?: iso})" }
                val text = if (lines.isEmpty()) "등록된 알림이 없어요." else "[알림]\n" + lines.joinToString("\n")
                ToolResultJson.success().put("data", text).toString()
            }
            is AppResult.Failure -> ToolResultJson.error("알림 목록 조회 실패")
        }
}
