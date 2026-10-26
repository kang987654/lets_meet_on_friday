package com.kosmos.app.domain.memory

/**
 * 사용자 말이 "앞에서 말한 것을 다시 들려 달라"는 되묻기인가 — 저장 요청이 함께 있으면 아니다.
 *
 * [WHY] 앞 대화에 숫자를 말해 둔 뒤 "송장번호 다시 알려줘"라고 하면 모델이 메모 저장을 불렀다 — 트리거 문구 다섯 판 모두 2~3/6(exp61).
 * 프롬프트로 안 고쳐져 앱이 그 호출을 막는다(BaseAgent). 규칙은 낱말 몇 개뿐이라 놓쳐도 지금처럼 승인 시트가 뜰 뿐이다.
 */
object RepeatRequest {
    private val REPEAT = Regex("다시\\s*(알려|말해|얘기|들려)|뭐였(지|더라|어)|뭐라고\\s*했")
    private val SAVE = Regex("기억해|저장|메모해|잊지\\s*마|적어\\s*(둬|놔|줘)")

    fun matches(userText: String): Boolean = REPEAT.containsMatchIn(userText) && !SAVE.containsMatchIn(userText)
}
