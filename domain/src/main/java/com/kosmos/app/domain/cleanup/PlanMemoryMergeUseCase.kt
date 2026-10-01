package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import javax.inject.Inject

/**
 * [PlanMemoryMergeUseCase]
 * 저장된 지식 노트에서 같은 사실이 여러 번 저장된 묶음을 찾아 병합 **제안**을 만듭니다 — 적용은 하지 않는다 (0.30.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Cleanup)
 * - **Dependencies**: [KnowledgeRepository], [ModelRunner](oneShot 판정), [MemoryMergeGuard](결정적 가드)
 *
 * ### Key Flow
 * 1. 노트를 읽어 [MemoryMergeGuard.candidatePairs] 로 후보 쌍을 낸다(숫자 일치 + 본문 겹침 ≥ 0.3).
 * 2. 쌍마다 oneShot 판정("같음/다름 + 합친 문장", exp42 v1 원문). "같음" 간선으로 묶는다.
 * 3. 3개 이상 묶음은 전체로 한 번 더 판정해 합친 문장을 얻는다. 합친 문장이 원문 숫자를 잃으면 버린다.
 *
 * [WHY] `oneShot = true` — 부수 계산이다. 빼면 채팅 KV 가 파괴된다 (ADR-010·014).
 */
class PlanMemoryMergeUseCase @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val modelRunner: ModelRunner
) {

    /**
     * @param onProgress (판정 끝난 수, 전체 판정 수) — 진행률 표시용
     */
    suspend operator fun invoke(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): AppResult<List<MergeProposal>> {
        val notes = when (val loaded = knowledgeRepository.getNotes(0, MAX_NOTES)) {
            is AppResult.Success -> loaded.data
            is AppResult.Failure -> return AppResult.Failure(loaded.error)
        }
        val pairs = MemoryMergeGuard.candidatePairs(notes)
        onProgress(0, pairs.size)

        val parent = IntArray(notes.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            return root
        }
        val pairMerged = mutableMapOf<Pair<Int, Int>, String>()
        pairs.forEachIndexed { index, (i, j) ->
            judge(listOf(notes[i].content, notes[j].content))?.let { merged ->
                pairMerged[i to j] = merged
                parent[find(i)] = find(j)
            }
            onProgress(index + 1, pairs.size)
        }

        val linked = pairMerged.keys.flatMap { listOf(it.first, it.second) }.toSet()
        val proposals = linked.groupBy { find(it) }.values.mapNotNull { members ->
            proposalFor(members.sorted(), pairMerged, notes)
        }
        return AppResult.Success(proposals)
    }

    private suspend fun proposalFor(
        members: List<Int>,
        pairMerged: Map<Pair<Int, Int>, String>,
        notes: List<KnowledgeNote>
    ): MergeProposal? {
        val sources = members.map { notes[it] }
        val merged = if (members.size == 2) {
            pairMerged[members[0] to members[1]]
        } else {
            judge(sources.map { it.content })
        } ?: return null
        // [WHY] 합친 문장이 원문 숫자를 하나라도 잃으면 제안하지 않는다 — 비밀번호·번호가 사라지는 병합은 되돌릴 수 없다.
        if (!MemoryMergeGuard.keepsNumbers(merged, sources.map { it.content })) return null
        return MergeProposal(sources, merged)
    }

    /** "같음"이면 합친 문장, 아니면(다름·형식 이탈·추론 실패) null. */
    private suspend fun judge(contents: List<String>): String? {
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = JUDGE_SYSTEM,
            history = emptyList(),
            currentInput = "기억들:\n" + contents.joinToString("\n") { "- $it" },
            oneShot = true
        )
        val text = (modelRunner.generate(prompt) as? AppResult.Success)?.data?.text ?: return null
        if (VERDICT.find(text)?.groupValues?.get(1) != SAME) return null
        return MERGED.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it != NONE }
    }

    companion object {
        const val SESSION_ID = "memory-merge"
        // [WHY] 1인 기억 규모(수백)에서 쌍 비교는 수 ms — 상한은 판정 횟수 폭주를 막는 안전판이다.
        private const val MAX_NOTES = 500
        private const val SAME = "같음"
        private const val NONE = "없음"
        private val VERDICT = Regex("판정\\s*[:：]\\s*(같음|다름)")
        private val MERGED = Regex("합친 문장\\s*[:：]\\s*(.+)")

        /** exp42 v1 원문 — 바꾸면 그 실측(오병합·숫자 보존)이 무효가 된다. */
        val JUDGE_SYSTEM = """
            너는 기억 정리 도우미다. 아래 기억들이 완전히 같은 사실을 말하는지 판정한다.
            숫자·날짜·시각·사람·대상이 하나라도 다르면 '다름'이다. 비슷한 주제일 뿐이어도 '다름'이다.
            같으면 숫자와 고유명사를 그대로 살려 가장 정확한 한 문장으로 합친다.
            출력은 정확히 두 줄:
            판정: 같음 또는 다름
            합친 문장: 같을 때 한 문장, 다르면 없음
        """.trimIndent()
    }
}
