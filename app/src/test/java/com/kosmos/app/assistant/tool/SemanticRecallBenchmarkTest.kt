package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.tool.Tokenizer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SemanticRecallBenchmarkTest]
 * 유의어·상위 개념·붙여쓰기·표현 변형 질의 세트(20쌍 × 질의 변형 = 40질의)에 대해
 * [SearchMemoryToolExecutor] 의 회수율을 결정적으로 측정하고 고정합니다 (0.39.6 M0).
 *
 * [WHY] 본문 글자만 겹치는 질문이 아니라 "음료 ↔ 커피/녹차", "반려동물 ↔ 강아지", "병원 ↔ 치과"처럼
 * 상위 개념·유의어로 물을 때 ① 본문만 있을 때(태그 없음) ② 쓰기 시점 상위 개념 태그가 있을 때
 * ③ 태그 목록 폴백을 통한 2차 재조회까지 각 단계가 얼마나 건지는지 숫자로 비교한다.
 */
@RunWith(RobolectricTestRunner::class)
class SemanticRecallBenchmarkTest {

    /**
     * @property id 정답 메모 식별자
     * @property content 저장된 메모 본문
     * @property literalTags 본문에 그대로 등장하는 명사만 붙인 태그(상위 개념 보강 전)
     * @property enrichedTags 상위 개념·유의어까지 포함해 붙인 태그(쓰기 시점 태그 보강 후)
     * @property queries 모델이 `search_memory(keyword=...)` 로 넘기는 대표 질의어와 그 표현 변형
     * @property kind 질의 유형: "surface"(붙여쓰기·일부 겹침), "synonym"(유의어), "hypernym"(상위 개념)
     */
    data class Case(
        val id: String,
        val content: String,
        val literalTags: List<String>,
        val enrichedTags: List<String>,
        val queries: List<String>,
        val kind: String
    )

    private val tokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 3
    }

    private fun inMemoryExecutor(notes: List<KnowledgeNote>, episodes: List<Episode> = emptyList()): SearchMemoryToolExecutor {
        val noteRepo = object : KnowledgeRepository {
            override suspend fun save(note: KnowledgeNote) = AppResult.Success(Unit)
            override suspend fun delete(noteId: String) = AppResult.Success(Unit)
            override suspend fun getById(noteId: String) = AppResult.Success(notes.find { it.id == noteId })
            override suspend fun search(query: String, limit: Int) = AppResult.Success(
                notes.filter { it.content.contains(query, ignoreCase = true) }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
            )
            override suspend fun searchRecent(limit: Int) = AppResult.Success(
                notes.sortedByDescending { it.createdAt }.take(limit)
            )
            override suspend fun searchByTags(tags: List<String>, limit: Int) = AppResult.Success(
                notes.filter { n -> tags.any { t -> n.tags.any { it.equals(t, ignoreCase = true) } } }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
            )
            override suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int) = AppResult.Success(emptyList<KnowledgeNote>())
            override suspend fun getNotes(offset: Int, limit: Int) = AppResult.Success(
                notes.sortedByDescending { it.createdAt }.drop(offset).take(limit)
            )
        }
        val epRepo = object : EpisodeRepository {
            private val summarized = episodes.filter { it.status == EpisodeStatus.SUMMARIZED }
            override suspend fun insert(episode: Episode) = AppResult.Success(Unit)
            override suspend fun update(episode: Episode) = AppResult.Success(Unit)
            override suspend fun getById(id: String) = AppResult.Success(episodes.find { it.id == id })
            override suspend fun getByStatus(status: EpisodeStatus) = AppResult.Success(episodes.filter { it.status == status })
            override suspend fun getEpisodes(offset: Int, limit: Int) = AppResult.Success(
                summarized.sortedByDescending { it.createdAt }.drop(offset).take(limit)
            )
            override suspend fun search(query: String, limit: Int) = AppResult.Success(
                summarized.filter {
                    it.title.orEmpty().contains(query, ignoreCase = true) ||
                        it.summary.orEmpty().contains(query, ignoreCase = true)
                }.sortedByDescending { it.createdAt }.take(limit)
            )
            override suspend fun searchByTags(tag: String, limit: Int) = AppResult.Success(
                summarized.filter { ep -> ep.tags.any { it.equals(tag, ignoreCase = true) } }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
            )
            override suspend fun delete(id: String) = AppResult.Success(Unit)
            override suspend fun mergeInto(fromId: String, into: Episode) = AppResult.Success(Unit)
        }
        return SearchMemoryToolExecutor(noteRepo, epRepo, tokenizer)
    }

    /** 폴백이 아닌 실제 검색 적중(1단 정밀 또는 2단 바이그램 스캔)에 정답 본문이 실렸는지 확인한다. */
    private fun isDirectHit(resultJson: String, targetContent: String): Boolean {
        val data = JSONObject(resultJson).optString("data")
        if (data.contains("일치하는 기억이 없습니다") || data.contains("저장된 기억이 하나도 없습니다")) return false
        return data.contains(targetContent)
    }

    @Test
    fun `표면 변형 질의는 태그 없이도 바이그램 스캔이 모두 건진다`() = runBlocking {
        val notes = CASES.mapIndexed { idx, c ->
            KnowledgeNote(id = c.id, content = c.content, tags = c.literalTags, createdAt = (idx + 1).toLong(), updatedAt = (idx + 1).toLong())
        }
        val executor = inMemoryExecutor(notes)

        val surfaceCases = CASES.filter { it.kind == "surface" }
        var hits = 0
        var total = 0
        for (c in surfaceCases) {
            for (q in c.queries) {
                total++
                val json = executor.execute(ToolArguments.of(mapOf("keyword" to q)), "s1")
                if (isDirectHit(json, c.content)) hits++
            }
        }
        assertEquals("표면 변형(붙여쓰기·부분 일치) 8질의는 모두 1차 적중해야 한다", total, hits)
    }

    /**
     * 상위 개념·유의어·붙여쓰기·표현 변형 20쌍 × 2질의 = 40질의.
     * - "surface" (4쌍 = 8질의): 붙여쓰기·축약어 등 바이그램 스캔으로 잡히는 표면 변형
     * - "partial" (5쌍 = 10질의): 한 어절("번호"·"이름"·"장소" 등)만 다른 메모와 겹치거나 부분 바이그램("회의"↔"대회의실")으로 걸리는 질의
     * - "pure_semantic" (11쌍 = 22질의): 본문과 글자가 전혀 안 겹치는 순수 상위 개념·유의어 질의 (본문 태그만으로는 0/22)
     */
    @Test
    fun `유의어와 상위 개념 질의는 본문 글자만으로는 놓치고 상위 개념 태그가 있으면 바로 찾는다`() = runBlocking {
        // 1. 본문에 있는 명사만 태그로 붙인 경우 (자동 추출의 기존 tagsFor 동작과 동일)
        val literalNotes = CASES.mapIndexed { idx, c ->
            KnowledgeNote(id = c.id, content = c.content, tags = c.literalTags, createdAt = (idx + 1).toLong(), updatedAt = (idx + 1).toLong())
        }
        val literalExecutor = inMemoryExecutor(literalNotes)

        // 2. 쓰기 시점에 상위 개념·유의어 태그까지 붙인 경우
        val enrichedNotes = CASES.mapIndexed { idx, c ->
            KnowledgeNote(id = c.id, content = c.content, tags = c.enrichedTags, createdAt = (idx + 1).toLong(), updatedAt = (idx + 1).toLong())
        }
        val enrichedExecutor = inMemoryExecutor(enrichedNotes)

        var literalHits = 0
        var enrichedHits = 0
        var pureSemanticLiteralHits = 0
        var pureSemanticEnrichedHits = 0
        var pureSemanticTotal = 0

        for (c in CASES) {
            for (q in c.queries) {
                val litJson = literalExecutor.execute(ToolArguments.of(mapOf("keyword" to q)), "s1")
                val enrJson = enrichedExecutor.execute(ToolArguments.of(mapOf("keyword" to q)), "s1")
                val litOk = isDirectHit(litJson, c.content)
                val enrOk = isDirectHit(enrJson, c.content)
                if (litOk) literalHits++
                if (enrOk) enrichedHits++
                if (c.kind == "pure_semantic") {
                    pureSemanticTotal++
                    if (litOk) pureSemanticLiteralHits++
                    if (enrOk) pureSemanticEnrichedHits++
                }
            }
        }

        // 본문 태그만 있을 때: 전체 40질의 중 18/40 적중(표면 변형 8/8 + 일반 명사·부분 겹침 10/10), 순수 상위 개념·유의어 22질의는 0/22.
        assertEquals("pureSemanticLiteralHits=$pureSemanticLiteralHits", 0, pureSemanticLiteralHits)
        assertEquals("literalHits=$literalHits", 18, literalHits)
        assertEquals(22, pureSemanticTotal)
        // 상위 개념·유의어 태그가 붙어 있으면 순수 의미 질의 22/22, 전체 40/40 적중한다.
        assertEquals(22, pureSemanticEnrichedHits)
        assertEquals(40, enrichedHits)
    }

    companion object {
        val CASES: List<Case> = listOf(
            // --- 표면 변형 (surface, 4쌍 = 8질의): 붙여쓰기·축약어·부분 낱말 ---
            Case(
                id = "s1",
                content = "자전거 자물쇠 비밀번호는 4821",
                literalTags = listOf("자전거", "자물쇠", "비밀번호"),
                enrichedTags = listOf("자전거", "자물쇠", "비밀번호"),
                queries = listOf("자물쇠번호", "자전거비번"),
                kind = "surface"
            ),
            Case(
                id = "s2",
                content = "집 현관문 도어락 번호는 2580",
                literalTags = listOf("현관문", "도어락"),
                enrichedTags = listOf("현관문", "도어락", "비밀번호"),
                queries = listOf("현관문번호", "도어락번호"),
                kind = "surface"
            ),
            Case(
                id = "s3",
                content = "회사 지하 주차장 자리는 B2 구역 C-14",
                literalTags = listOf("주차장", "자리"),
                enrichedTags = listOf("주차장", "주차", "차량"),
                queries = listOf("주차장자리", "지하주차"),
                kind = "surface"
            ),
            Case(
                id = "s4",
                content = "거실 공유기 와이파이 암호는 kosmos7788",
                literalTags = listOf("공유기", "와이파이", "암호"),
                enrichedTags = listOf("공유기", "와이파이", "비밀번호", "인터넷"),
                queries = listOf("와이파이암호", "공유기암호"),
                kind = "surface"
            ),

            // --- 일반 명사·부분 바이그램 겹침 (partial, 5쌍 = 10질의): 한 어절은 겹치고 나머지 어절이 유의어·상위 개념인 질의 ---
            Case(
                id = "p1",
                content = "우리 집 골든리트리버 강아지 이름은 콩이",
                literalTags = listOf("골든리트리버", "강아지", "콩이"),
                enrichedTags = listOf("골든리트리버", "강아지", "콩이", "반려동물", "애완동물"),
                queries = listOf("애완동물 이름", "키우는 동물 이름"),
                kind = "partial"
            ),
            Case(
                id = "p2",
                content = "혈압약은 매일 아침 식후 30분에 한 알 복용",
                literalTags = listOf("혈압약", "아침", "식후"),
                enrichedTags = listOf("혈압약", "약", "복용약", "건강"),
                queries = listOf("복용약", "아침 복용"),
                kind = "partial"
            ),
            Case(
                id = "p3",
                content = "회사 개인 락커 번호는 214번",
                literalTags = listOf("회사", "락커"),
                enrichedTags = listOf("회사", "락커", "사물함", "보관함", "번호"),
                queries = listOf("사물함 번호", "보관함 번호"),
                kind = "partial"
            ),
            Case(
                id = "p4",
                content = "노트북 로그인 PIN 은 0912",
                literalTags = listOf("노트북", "로그인", "PIN"),
                enrichedTags = listOf("노트북", "컴퓨터", "로그인", "비밀번호", "암호"),
                queries = listOf("노트북 비밀번호", "노트북 암호"),
                kind = "partial"
            ),
            Case(
                id = "p5",
                content = "다음 주 금요일 팀 미팅 장소는 본관 5층 대회의실",
                literalTags = listOf("금요일", "미팅", "대회의실"),
                enrichedTags = listOf("미팅", "회의", "대회의실", "회의실", "장소"),
                queries = listOf("회의 장소", "회의실"),
                kind = "partial"
            ),

            // --- 순수 상위 개념·유의어 (pure_semantic, 11쌍 = 22질의): 본문 글자와 겹침이 없어 태그 없이는 0건 ---
            Case(
                id = "h1",
                content = "아침에는 커피보다 녹차를 더 마심",
                literalTags = listOf("커피", "녹차"),
                enrichedTags = listOf("커피", "녹차", "음료", "차", "선호"),
                queries = listOf("음료", "선호 음료"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h2",
                content = "땅콩과 새우를 먹으면 두드러기가 남",
                literalTags = listOf("땅콩", "새우", "두드러기"),
                enrichedTags = listOf("땅콩", "새우", "두드러기", "알레르기", "음식"),
                queries = listOf("알레르기", "못 먹는 음식"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h3",
                content = "강남역 미소치과에서 매달 정기 검진 받음",
                literalTags = listOf("강남역", "미소치과", "검진"),
                enrichedTags = listOf("강남역", "미소치과", "치과", "병원", "검진"),
                queries = listOf("다니는 병원", "병원"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h4",
                content = "주말 아침마다 한강에서 5km 러닝을 함",
                literalTags = listOf("한강", "러닝"),
                enrichedTags = listOf("한강", "러닝", "운동", "취미", "달리기"),
                queries = listOf("운동", "취미 생활"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h5",
                content = "매주 수요일 저녁 8시 박민수 선생님께 통기타 레슨",
                literalTags = listOf("수요일", "박민수", "통기타", "레슨"),
                enrichedTags = listOf("통기타", "기타", "레슨", "악기", "학원"),
                queries = listOf("배우는 악기", "악기"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h6",
                content = "어머니 생신은 음력 11월 7일",
                literalTags = listOf("어머니", "생신"),
                enrichedTags = listOf("어머니", "엄마", "생신", "생일", "가족", "기념일"),
                queries = listOf("가족 기념일", "엄마 생일"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h7",
                content = "넷플릭스 멤버십은 매달 15일에 신한카드로 자동이체됨",
                literalTags = listOf("넷플릭스", "멤버십", "신한카드"),
                enrichedTags = listOf("넷플릭스", "구독", "정기결제", "신한카드"),
                queries = listOf("구독 서비스", "정기결제"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h8",
                content = "나이키 에어맥스는 270mm 를 신음",
                literalTags = listOf("나이키", "에어맥스"),
                enrichedTags = listOf("나이키", "에어맥스", "신발", "운동화", "사이즈", "치수"),
                queries = listOf("신발 치수", "구두 치수"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h9",
                content = "트렁크 우산 옆에 비상용 우비를 넣어 둠",
                literalTags = listOf("트렁크", "우산", "우비"),
                enrichedTags = listOf("트렁크", "우산", "우비", "비옷", "차량"),
                queries = listOf("비옷", "비옷 보관"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h10",
                content = "대한항공 스카이패스 코드는 9081-2234",
                literalTags = listOf("대한항공", "스카이패스"),
                enrichedTags = listOf("대한항공", "스카이패스", "마일리지", "적립"),
                queries = listOf("마일리지", "마일리지 적립"),
                kind = "pure_semantic"
            ),
            Case(
                id = "h11",
                content = "집으로 시킨 소설책 운송장 코드 6043-1122-9087",
                literalTags = listOf("소설책", "운송장"),
                enrichedTags = listOf("소설책", "운송장", "택배", "배송", "조회"),
                queries = listOf("택배 배송", "택배 조회"),
                kind = "pure_semantic"
            )
        )
    }
}
