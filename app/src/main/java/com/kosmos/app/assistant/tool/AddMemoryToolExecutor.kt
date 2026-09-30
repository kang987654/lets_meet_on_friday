package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.security.ApprovalRules
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import javax.inject.Inject

/**
 * [AddMemoryToolExecutor]
 * 모델의 `AddMemory` 툴 콜을 받아 사용자 지식(Knowledge)을 영구 저장하는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [SaveKnowledgeUseCase]
 *
 * ### Key Flow
 * 1. 영구 상태 변경이므로 사용자 승인(MEMORY_WRITE)을 거친 후에만 실행됩니다 (BaseAgent 공통 경로).
 * 2. content/tags 인자를 파싱하여 [SaveKnowledgeUseCase]로 저장합니다.
 */
class AddMemoryToolExecutor @Inject constructor(
    private val saveKnowledgeUseCase: SaveKnowledgeUseCase
) : ToolExecutor {
    override val name: String = ToolNames.ADD_MEMORY

    // [WHY] 메모리 저장은 영구 상태 변경이자 이후 모든 대화의 컨텍스트를 오염시킬 수 있는
    // 경로이므로(주입된 문서가 가짜 '기억'을 심는 벡터) 사용자 승인을 요구한다.
    override val actionType: ApprovalRules.ActionType = ApprovalRules.ActionType.MEMORY_WRITE

    // [WHY] 승인과 실행이 같은 필수 인자 검증을 통과하도록 requireString 을 양쪽에서 쓴다.
    // 내용이 없으면 승인 카드를 띄우지 않고 곧바로 오류가 모델에게 돌아간다.
    override fun buildApprovalRequest(args: ToolArguments, sessionId: String): ApprovalRequest {
        val content = args.requireString("content")
        val preview = if (content.length > 80) content.take(80) + "…" else content
        return ApprovalRequest(
            sessionId = sessionId,
            title = "메모리 저장 승인",
            description = "저장할 내용: $preview"
        )
    }

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val content = args.requireString("content")
        // [WHY] 기존 구현은 `tagsRaw is List<*>` 로 분기했는데, org.json 은 JSON 배열을
        // List 가 아닌 JSONArray 로 주므로 이 분기가 절대 참이 되지 않았다. 결과적으로
        // 프롬프트가 지시한 정식 형태(`{"tags":["work","urgent"]}`)로 보낸 태그가 전부
        // 조용히 버려지고 콤마 문자열 폴백만 동작했다.
        val tags = args.stringList("tags")

        // [WHY] 문구는 exp38 실측으로 정했다(발화 6개). 실패 문구가 사실만 말하면("Failed to save
        // memory: DbWriteError…" 영어 2/6, 한국어 번역 3/6) 모델이 오류를 무시하고 "기억해 두었습니다"
        // 라고 **거짓 성공**을 말했다 — 해야 할 행동을 적은 지시형은 0/6(+5토큰). 내부 오류 이름도
        // 모델 입력에서 뺐다(진단은 감사 로그가 맡는다). 성공 문구는 영/한 동등이라 다른 실행기와 맞췄다.
        return when (saveKnowledgeUseCase(content, tags)) {
            is AppResult.Success -> ToolResultJson.success().put("message", "기억에 저장했어요.").toString()
            is AppResult.Failure -> ToolResultJson.error(
                "저장에 실패했습니다. 기억하지 못했다고 사용자에게 분명히 알리고, 다시 시도할지 물어보세요."
            )
        }
    }
}
