package com.kosmos.app.runtime.gemma

import android.util.Log
import com.google.ai.edge.litertlm.Conversation
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelToolCall

/**
 * [RuntimeDiagnostics]
 * GemmaModelRunner 의 디버그 진단 로그(KV 사용량·프리페이스·턴 요약)입니다. 동작에는 관여하지 않는다.
 *
 * [WHY] 러너 본문(수명주기·턴 실행)과 진단 로그가 한 파일에 섞여 700줄을 넘겼다. 진단은 전부 디버그
 * 빌드 한정이고 실기기 판정의 증거라 지울 수 없으니, 읽는 사람이 동작 코드만 볼 수 있게 떼어 둔다.
 */
internal object RuntimeDiagnostics {
    private const val TAG = "GemmaModelRunner"

    // [WHY] logcat 한 줄의 실용 한계보다 넉넉히 아래로 잡는다. 프리페이스 전체(툴 선언 포함)를
    // 남기려면 자르지 않고 **나눠서** 찍어야 한다.
    private const val PREFACE_CHUNK = 1500

    /**
     * KV 실사용량을 send 경계에서 남깁니다.
     *
     * [WHY] KV 사용량은 다음 턴 시작(재설정 판정)에서야 보였다 — 툴 회신이 턴 중간에 몇 토큰을 먹는지,
     * 턴이 끝난 시점에 용량 대비 어디까지 왔는지가 로그에 없어서 조용한 초과를 실기기에서 판정할 수
     * 없었다. AAR 은 용량을 넘겨도 오류를 내지 않으므로(파이썬 0.15.0+ 은 거부, exp17) 이 로그가 초과와
     * 글자 깨짐의 시간적 상관을 확정할 유일한 증거다 (ADR-020 실기기 재검증 프로토콜).
     * [WHY] send 경계에서만 읽는다 — 스트리밍 중 네이티브 getTokenCount 호출은 생성과 경합한다.
     */
    fun logKvUsage(stage: String, conversation: Conversation) {
        if (!com.kosmos.app.BuildConfig.DEBUG) return
        val tokens = runCatching { conversation.getTokenCount() }.getOrNull() ?: return
        if (tokens > Constants.ENGINE_MAX_TOKENS) {
            Log.w(TAG, "KV 용량 초과 상태로 계속 진행: $stage tokens=$tokens > engine=${Constants.ENGINE_MAX_TOKENS}")
        } else {
            Log.d(TAG, "KV usage: $stage tokens=$tokens / ${Constants.ENGINE_MAX_TOKENS}")
        }
    }

    /**
     * [WHY] 초과를 조용히 넘기지 않는다. AAR 0.14.0 은 KV 용량을 넘겨도 오류 없이 진행했고(품질 저하로만
     * 드러남), 0.16.0 은 `4097 >= 4096` 오류로 거부한다(2026-08-13 실기기) — 어느 쪽이든 경계에 닿는 것을
     * 재설정 판정 시점에 눈에 보이게 남긴다.
     */
    fun warnIfOverCeiling(tokens: Int) {
        if (com.kosmos.app.BuildConfig.DEBUG && tokens > Constants.PREFILL_CEILING_TOKENS) {
            Log.w(
                TAG,
                "KV 천장 초과: tokens=$tokens > ceiling=${Constants.PREFILL_CEILING_TOKENS} " +
                    "(engine=${Constants.ENGINE_MAX_TOKENS}). 예산·오버헤드 상수를 다시 볼 것."
            )
        }
    }

    /**
     * [WHY] 툴 호출 여부는 실기기에서만 확인할 수 있고 감사 로그에는 시스템 지시가 남지 않는다.
     * enabledTools 를 함께 남겨야 toolCalls=[] 가 "선언 안 됨"인지 "모델이 거부"인지 구분된다.
     */
    fun logTurn(prompt: ChatPrompt, text: String, toolCalls: List<ModelToolCall>) {
        Log.d(
            TAG,
            "turn done: textLen=${text.length}, toolCalls=${toolCalls.map { it.name }}, " +
                "enabledTools=${prompt.enabledTools}"
        )
    }

    /**
     * [WHY] toolCalls=[] 만으로는 "선언이 템플릿에 안 들어감"과 "모델이 호출을 거부"를 구분할 수 없다.
     * 렌더링된 프리페이스에 툴 블록이 있는지가 모델 파일의 템플릿 지원 여부를 실기기에서 확정하는
     * 유일한 단서다 (Gemma 3n 계열 템플릿에는 툴 블록이 없다).
     * [WHY] 디버그 빌드로 한정한다 — `renderPrefaceIntoString()` 은 템플릿 전체 렌더이고 수명주기
     * 뮤텍스 안에서 일어난다. 프리페이스를 logcat 에 남기는 것 자체도 사용자 발화가 새는 경로다.
     * [WHY] 조각으로 나눠 찍는다 — 예전 `take(2000)` 은 툴 선언 블록 중간에서 잘려 정작 답이 안 보였다.
     * [WHY] renderPrefaceIntoString 은 @ExperimentalApi — 진단용이라 API 가 사라져도 로그만 잃는다.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    fun logConversationCreated(prompt: ChatPrompt, created: Conversation) {
        if (!com.kosmos.app.BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "conversation created: tools=${prompt.enabledTools}, " +
                "providers=${KosmosToolDeclarations.providersFor(prompt.enabledTools).size}"
        )
        runCatching { created.renderPrefaceIntoString() }
            .onSuccess { preface ->
                Log.d(TAG, "preface: ${preface.length}자, ${PREFACE_CHUNK}자씩")
                preface.chunked(PREFACE_CHUNK).forEachIndexed { index, chunk ->
                    Log.d(TAG, "preface[$index] $chunk")
                }
            }
            .onFailure { e -> Log.d(TAG, "preface render failed: ${e.message}") }
    }
}
