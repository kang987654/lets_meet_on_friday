package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import java.util.UUID
import javax.inject.Inject

/**
 * [ApplyMemoryMergeUseCase]
 * 사용자가 체크한 병합 제안을 적용합니다 — 합친 노트 저장 → 원본 삭제 → 감사 기록 (0.30.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Cleanup)
 * - **Dependencies**: [KnowledgeRepository], [AuditTrailService]
 *
 * [WHY] 저장이 먼저다 — 저장이 실패하면 원본을 지우지 않는다(기억이 사라지는 쪽의 실패만은 막는다).
 * [WHY] 원문은 감사 로그에 남긴다 — 되돌리기가 없는 대신 무엇을 합쳤는지 추적할 수 있게(사용자 결정 D3).
 */
class ApplyMemoryMergeUseCase @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val auditTrailService: AuditTrailService
) {

    /** @return 적용된 제안 수 */
    suspend operator fun invoke(proposals: List<MergeProposal>, now: Long = System.currentTimeMillis()): Int {
        val consumed = mutableSetOf<String>()
        var applied = 0
        for (proposal in proposals) {
            // [WHY] 묶음이 겹치면(같은 노트가 두 제안에) 먼저 적용된 쪽만 — 이미 지운 노트를 다시 합치지 않는다.
            if (proposal.sources.any { it.id in consumed }) continue
            val merged = KnowledgeNote(
                id = UUID.randomUUID().toString(),
                content = proposal.mergedContent,
                tags = proposal.sources.flatMap { it.tags }.distinct(),
                createdAt = proposal.sources.minOf { it.createdAt },
                updatedAt = now,
                // [WHY] 하나라도 사용자가 직접 저장한 것이면 수동 — "자동" 배지가 사용자 기억을 자동 추출로 오인시키지 않게(D2).
                source = if (proposal.sources.any { it.source == KnowledgeNote.SOURCE_MANUAL }) {
                    KnowledgeNote.SOURCE_MANUAL
                } else {
                    KnowledgeNote.SOURCE_AUTO
                }
            )
            if (knowledgeRepository.save(merged) !is AppResult.Success) continue
            proposal.sources.forEach { knowledgeRepository.delete(it.id) }
            consumed += proposal.sources.map { it.id }
            applied++
            auditTrailService.logToolCall(
                sessionId = PlanMemoryMergeUseCase.SESSION_ID,
                toolName = "MemoryMerge",
                resultJson = proposal.sources.joinToString(" + ") { it.content } + " → " + proposal.mergedContent
            )
        }
        return applied
    }
}
