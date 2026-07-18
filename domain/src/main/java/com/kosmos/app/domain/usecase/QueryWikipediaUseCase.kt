package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.tool.WikipediaSearchTool
import javax.inject.Inject

/**
 * [QueryWikipediaUseCase]
 * 지정된 토픽에 대해 위키피디아 검색을 수행하고 요약 정보를 반환하는 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [WikipediaSearchTool]
 *
 * ### Key Flow
 * 1. 검색어(Topic)와 언어(Lang) 매개변수를 수신합니다.
 * 2. [WikipediaSearchTool] 인터페이스에 쿼리를 위임하고 검색된 `AppResult<String>` 결과를 반환합니다.
 */
class QueryWikipediaUseCase @Inject constructor(
    private val wikipediaSearchTool: WikipediaSearchTool
) {
    suspend operator fun invoke(topic: String, lang: String): AppResult<String> {
        return wikipediaSearchTool.search(topic, lang)
    }
}
