package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.domain.usecase.ExtractFactsUseCase
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [EpisodeFactExtractorTest]
 * 자동 추출의 저장 규칙을 못박습니다 — 토글·발열 게이트, 지식 중복 skip, 프로필 동일값·
 * 제안 이력 skip, PENDING 저장, 감사 1건.
 *
 * [WHY] "거절한 사실을 다시 묻지 않는다"와 "지식 자동 저장은 중복을 만들지 않는다"가
 * 이 기능이 잔소리·쓰레기로 전락하지 않는 두 경계다.
 */
class EpisodeFactExtractorTest {

    private val settingsDataStore: SettingsDataStore = mockk {
        every { autoExtractEnabledFlow } returns flowOf(true)
    }
    private val extractFacts: ExtractFactsUseCase = mockk()
    private val saveKnowledge: SaveKnowledgeUseCase = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val profileRepository: ProfileRepository = mockk()
    private val suggestionRepository: ProfileSuggestionRepository = mockk()
    private val auditTrailService: AuditTrailService = mockk(relaxed = true)
    private val metricsCollector: RuntimeMetricsCollector = mockk {
        every { getCurrentTemp() } returns 30f
    }

    private fun extractor() = EpisodeFactExtractor(
        settingsDataStore, extractFacts, saveKnowledge, knowledgeRepository,
        profileRepository, suggestionRepository, auditTrailService, metricsCollector
    )

    private val episode = Episode(
        id = "e1", sessionId = "s1", status = EpisodeStatus.SUMMARIZED, title = "t", summary = "s",
        tags = listOf("회식", "금요일", "강남", "삼겹살", "회사"), startAt = 1, endAt = 2,
        messageCount = 2, retryCount = 0, createdAt = 1, updatedAt = 2
    )
    private val messages = listOf(
        ChatMessage(id = "m1", sessionId = "s1", role = ChatMessage.Role.USER, content = "x", inputType = InputType.TEXT, createdAt = 1)
    )

    @Before
    fun defaults() {
        coEvery { knowledgeRepository.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { saveKnowledge(any(), any(), any()) } answers {
            AppResult.Success(KnowledgeNote(id = "k", content = firstArg(), createdAt = 0, updatedAt = 0))
        }
        coEvery { profileRepository.getEntries() } returns AppResult.Success(emptyList())
        coEvery { suggestionRepository.exists(any(), any()) } returns false
        coEvery { suggestionRepository.insert(any()) } returns AppResult.Success(Unit)
    }

    private fun facts(profile: List<Pair<String, String>> = emptyList(), knowledge: List<String> = emptyList()) {
        coEvery { extractFacts(any()) } returns
            AppResult.Success(ExtractFactsUseCase.ExtractedFacts(profile = profile, knowledge = knowledge))
    }

    @Test
    fun `토글이 꺼져 있으면 추론 자체를 하지 않는다`() = runBlocking {
        every { settingsDataStore.autoExtractEnabledFlow } returns flowOf(false)

        val outcome = extractor().extract(episode, messages)

        assertNull(outcome)
        coVerify(exactly = 0) { extractFacts(any()) }
    }

    @Test
    fun `발열 경고 이상이면 건너뛴다`() = runBlocking {
        every { metricsCollector.getCurrentTemp() } returns 44f

        assertNull(extractor().extract(episode, messages))
        coVerify(exactly = 0) { extractFacts(any()) }
    }

    @Test
    fun `지식은 source auto 와 요약 태그 3개로 저장된다`() = runBlocking {
        facts(knowledge = listOf("회식은 매달 마지막 금요일 고기굽는집"))

        val outcome = extractor().extract(episode, messages, now = 1_000)

        assertEquals(1, outcome!!.savedKnowledge)
        coVerify(exactly = 1) {
            saveKnowledge("회식은 매달 마지막 금요일 고기굽는집", listOf("회식", "금요일", "강남"), KnowledgeNote.SOURCE_AUTO)
        }
    }

    @Test
    fun `이미 같은 내용이 있으면 지식을 저장하지 않는다`() = runBlocking {
        facts(knowledge = listOf("와이파이 kosmos123"))
        coEvery { knowledgeRepository.search("와이파이 kosmos123", 1) } returns AppResult.Success(
            listOf(KnowledgeNote(id = "old", content = "집 와이파이 kosmos123", createdAt = 0, updatedAt = 0))
        )

        val outcome = extractor().extract(episode, messages)

        assertEquals(0, outcome!!.savedKnowledge)
        coVerify(exactly = 0) { saveKnowledge(any(), any(), any()) }
    }

    @Test
    fun `프로필 급은 PENDING 제안으로 저장된다`() = runBlocking {
        facts(profile = listOf("이름" to "진우"))
        val inserted = slot<ProfileSuggestion>()
        coEvery { suggestionRepository.insert(capture(inserted)) } returns AppResult.Success(Unit)

        val outcome = extractor().extract(episode, messages, now = 5_000)

        assertEquals(1, outcome!!.suggestedProfile)
        assertEquals("이름", inserted.captured.key)
        assertEquals("진우", inserted.captured.value)
        assertEquals("e1", inserted.captured.episodeId)
        assertEquals(ProfileSuggestionStatus.PENDING, inserted.captured.status)
        assertEquals(5_000L, inserted.captured.createdAt)
        coVerify(exactly = 0) { profileRepository.upsert(any(), any(), any()) }
    }

    @Test
    fun `현재 프로필과 같은 값은 제안하지 않는다`() = runBlocking {
        facts(profile = listOf("이름" to "진우", "직업" to "개발자"))
        coEvery { profileRepository.getEntries() } returns AppResult.Success(listOf(ProfileEntry("이름", "진우")))

        val outcome = extractor().extract(episode, messages)

        assertEquals(1, outcome!!.suggestedProfile)
        coVerify(exactly = 1) { suggestionRepository.insert(match { it.key == "직업" }) }
    }

    @Test
    fun `이미 제안된 키-값은 거절됐더라도 다시 묻지 않는다`() = runBlocking {
        facts(profile = listOf("거주지" to "부산"))
        coEvery { suggestionRepository.exists("거주지", "부산") } returns true

        val outcome = extractor().extract(episode, messages)

        assertEquals(0, outcome!!.suggestedProfile)
        coVerify(exactly = 0) { suggestionRepository.insert(any()) }
    }

    @Test
    fun `추출 실패는 아무것도 저장하지 않고 감사도 남기지 않는다`() = runBlocking {
        coEvery { extractFacts(any()) } returns AppResult.Failure(AppError.ModelInferenceError("형식"))

        assertNull(extractor().extract(episode, messages))
        coVerify(exactly = 0) { saveKnowledge(any(), any(), any()) }
        coVerify(exactly = 0) { suggestionRepository.insert(any()) }
        coVerify(exactly = 0) { auditTrailService.logModelRun(any(), any(), any(), any()) }
    }

    @Test
    fun `성공 시 감사 MODEL_RUN 이 개수만 담아 1건 남는다`() = runBlocking {
        facts(profile = listOf("이름" to "진우"), knowledge = listOf("사실"))

        extractor().extract(episode, messages)

        coVerify(exactly = 1) {
            auditTrailService.logModelRun(
                ExtractFactsUseCase.SESSION_ID,
                match { it.contains("episode=e1") },
                match { it.contains("savedKnowledge=1") && it.contains("suggested=1") && !it.contains("진우") },
                any()
            )
        }
    }
}
