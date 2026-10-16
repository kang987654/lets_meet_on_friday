package com.kosmos.app.domain.model

/**
 * [MessageResult]
 * 비서 메시지가 만든(또는 근거로 쓴) 대상 — 채팅 결과 카드가 이 값으로 대상을 **다시 조회해 현재 상태**를 그린다(0.38.2, D6 ④).
 *
 * [WHY] 본문을 파싱하지 않는다 — 문장은 모델이 바꿔 말하므로 id 가 유일한 근거다. 옛 메시지는 null(글만 보인다).
 *
 * @property ref 대상 id — [Kind.REMINDER]·[Kind.SCHEDULE] 는 task id, [Kind.MEMORY_NOTE] 는 note id,
 *   [Kind.MEMORY_RECALL] 은 근거 메모 id(없으면 빈 문자열 — 과거 대화만 근거).
 * @property meta 작은 JSON(기억 답의 근거·충돌 값 등). 화면 표시용이며 모델 입력에는 들어가지 않는다.
 */
data class MessageResult(
    val kind: Kind,
    val ref: String,
    val meta: String? = null
) {
    enum class Kind {
        /** AddReminder 가 만든 리마인더 */
        REMINDER,
        /** AddSchedule 이 만든 일정 */
        SCHEDULE,
        /** AddMemory 가 저장한 메모 */
        MEMORY_NOTE,
        /** SearchMemory 로 답한 기억(근거 메모·과거 대화) */
        MEMORY_RECALL
    }
}
