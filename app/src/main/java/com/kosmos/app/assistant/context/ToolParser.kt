package com.kosmos.app.assistant.context

import com.kosmos.app.assistant.tool.ToolArguments

/**
 * [ToolParser]
 * 스트리밍되는 LLM 응답 텍스트 내에서 특정 태그(`<|think|>`, `<tool_call>`)를 실시간으로 추출하고 걷어내는 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context/Parsing)
 * - **Dependencies**: [ToolArguments]
 *
 * ### Key Flow
 * 1. 전체 응답 텍스트에서 `<|think|>` 블록을 정규식으로 탐색하여 에이전트의 사고 과정(thinking) 추출 및 본문에서 제거
 * 2. `<tool_call>` 블록은 본문에서 걷어내기만 한다(툴 호출은 런타임의 구조화된 toolCalls 가 담당, ADR-008)
 *    — 모델이 옛 프로토콜 문법을 본문으로 흘렸을 때 화면에 새지 않게 하는 표시 위생 처리다.
 * 3. 최종적으로 UI 렌더링을 위한 본문 텍스트와 사고 과정을 캡슐화한 [ParsedStream] 반환
 */
object ToolParser {

    data class ParsedStream(
        val content: String,
        val thinking: String?
    )

    data class ToolCallData(
        val name: String,
        val args: ToolArguments
    )

    fun parseStream(rawString: String): ParsedStream {
        var text = rawString
        var thinkingProcess: String? = null

        // 1. Extract <|think|> blocks
        // [WHY] findAll 이다 — 예전에는 find(단수)여서 두 번째 이후 생각 블록이 본문에 남아
        // 프로토콜 문법이 그대로 노출됐다.
        //
        // [WHY] 공식 문서(capabilities/thinking)는 *"모델이 생성한 thought 는 히스토리로 되돌리기
        // 전에 삭제하되 **툴 호출 시퀀스 중에는 예외**"* 라고 규정한다. 이 삭제가 그 규칙을 어기지
        // 않는 이유를 적어 둔다 — 여기서 걷어내는 것은 **화면과 DB 에 남길 본문**이고, 모델에게
        // 되돌아가는 문맥이 아니다. 대화를 재사용하는 경로에서는 런타임이 자기 KV 에 생성 토큰을
        // 그대로 들고 있고, 재생성 경로에서는 DB 의 thought 없는 텍스트를 싣는데 그것이 문서의
        // 기본 규칙과 같다. 예외에 해당하는 "툴 시퀀스 중 재생성" 은 `GemmaModelRunner` 가 토큰
        // 초과보다 우선해 막는다(0.8.5 실기기 사고 때문에 넣은 가드).
        //
        // 되짚어야 할 것: 생각 모드를 **켜게 되면** 이 관계를 다시 확인해야 한다. 지금은 꺼져
        // 있어(exp24 근거) 걷어낼 thought 자체가 나오지 않는다.
        // [WHY] `|$` 대안은 스트리밍 때문에 필요하다. 닫는 태그가 아직 안 온 생각 블록도
        // 본문에서 걷어내야 한다(그 구간의 content 는 비게 되고, 호출부가 null 로 정규화한다).
        val thinkRegex = Regex("<\\|think\\|>(.*?)(</\\|think\\|>|$)", RegexOption.DOT_MATCHES_ALL)
        val thinkMatches = thinkRegex.findAll(text).toList()
        if (thinkMatches.isNotEmpty()) {
            thinkingProcess = thinkMatches
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .ifEmpty { null }
            thinkMatches.forEach { text = text.replace(it.value, "") }
        }

        // 2. Strip <tool_call> blocks from content (ADR-008)
        val toolRegex = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
        for (match in toolRegex.findAll(text).toList()) {
            text = text.replace(match.value, "")
        }

        return ParsedStream(
            content = stripIncompleteTag(text).trimStart(),
            thinking = thinkingProcess
        )
    }

    /**
     * 아직 닫히지 않은 태그와 토큰 경계에 걸린 태그 조각을 본문에서 잘라냅니다.
     *
     * [WHY] `toolRegex` 는 닫는 태그를 요구하므로, 스트리밍 중 열려 있는 `<tool_call>` 은
     * 매칭되지 않고 본문에 그대로 남아 화면에 렌더됐다. 툴 콜 턴에서는 가시 구간 대부분 동안
     * `<tool_call>{"name":"AddSch` 가 한 글자씩 자라는 것이 보였다.
     *
     * [WHY] 꼬리의 부분 접두사(`<tool_c`, `<|th`, 심지어 `<`)도 자른다. 다음 토큰이 오면
     * 복원되므로 손실이 없고, 자르지 않으면 태그가 완성되는 순간까지 조각이 노출된다.
     * 모델이 실제로 쓴 `<` 하나가 한 틱 늦게 보이는 것은 그 대가로 받아들인다.
     *
     * [WHY] `parseStream` 밖에서도 쓸 수 있게 공개했다. 태그가 아직 완성되지 않은 구간에서는
     * 전체 정규식 파싱이 불필요하지만(누적 문자열이 곧 본문) 꼬리 조각은 여전히 잘라야 한다.
     * `BaseAgent` 가 O(n²) 파싱을 피하면서 이 함수만 호출한다.
     */
    fun stripIncompleteTag(text: String): String {
        // 닫히지 않은 채 남은 완전한 여는 태그부터 잘라낸다.
        val openIndex = OPEN_TAGS.mapNotNull { tag ->
            text.indexOf(tag).takeIf { it >= 0 }
        }.minOrNull()
        if (openIndex != null) return text.substring(0, openIndex)

        // 꼬리에 걸린 부분 접두사를 잘라낸다.
        for (tag in OPEN_TAGS) {
            for (length in (tag.length - 1) downTo 1) {
                if (text.endsWith(tag.substring(0, length))) {
                    return text.substring(0, text.length - length)
                }
            }
        }
        return text
    }

    private val OPEN_TAGS = listOf("<tool_call>", "<|think|>")
}
