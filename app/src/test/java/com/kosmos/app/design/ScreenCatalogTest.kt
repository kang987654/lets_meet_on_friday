package com.kosmos.app.design

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.ui.component.LocalClock
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.ThemeMode
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import javax.inject.Inject

/**
 * [ScreenCatalogTest]
 * 화면 기준 이미지 — 주요 화면을 **실제 ViewModel + 가짜 데이터**로 라이트·다크 Roborazzi 이미지로 찍습니다(대화상자·시트 창 합성).
 *
 * [WHY] 에뮬레이터는 엔진 초기화가 끝나지 않아(2026-10-14) 스플래시를 못 지난다 — Hilt 테스트 그래프로 ViewModel 을 조립해
 * Robolectric 네이티브 그래픽으로 그린다(시스템 바·키보드·실제 글꼴 차이는 못 본다 → 실기기).
 * 기준 이미지는 `app/src/test/screenshots/`(커밋). 기록·검증 명령은 DESIGN.md "유지" 절. 시각·시간대는 고정한다(AGENTS §2-④).
 */
@OptIn(ExperimentalRoborazziApi::class)
@HiltAndroidTest
@UninstallModules(com.kosmos.app.di.ModelModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenCatalogTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    @Inject lateinit var sessionStore: com.kosmos.app.data.local.prefs.SessionStore
    @Inject lateinit var conversationRepository: com.kosmos.app.domain.memory.ConversationRepository
    @Inject lateinit var episodeRepository: com.kosmos.app.domain.memory.EpisodeRepository
    @Inject lateinit var knowledgeRepository: com.kosmos.app.domain.memory.KnowledgeRepository
    @Inject lateinit var taskRepository: com.kosmos.app.domain.memory.TaskRepository
    @Inject lateinit var profileRepository: com.kosmos.app.domain.memory.ProfileRepository
    @Inject lateinit var suggestionRepository: com.kosmos.app.domain.memory.ProfileSuggestionRepository
    @Inject lateinit var auditRepository: com.kosmos.app.domain.memory.AuditRepository
    @Inject lateinit var briefingGenerator: com.kosmos.app.assistant.briefing.MorningBriefingGenerator
    @Inject lateinit var sendChatMessageUseCase: com.kosmos.app.domain.usecase.SendChatMessageUseCase
    @Inject lateinit var approvalCoordinator: com.kosmos.app.assistant.approval.ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: com.kosmos.app.platform.share.ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
    @Inject lateinit var settingsDataStore: com.kosmos.app.data.local.prefs.SettingsDataStore
    @Inject lateinit var runtimeManager: com.kosmos.app.runtime.gemma.GemmaRuntimeManager
    @Inject lateinit var briefingScheduler: com.kosmos.app.work.BriefingNotificationScheduler
    @Inject lateinit var exportMemoryUseCase: com.kosmos.app.domain.usecase.ExportMemoryUseCase
    @Inject lateinit var importMemoryUseCase: com.kosmos.app.domain.usecase.ImportMemoryUseCase
    @Inject lateinit var backupFileWriter: com.kosmos.app.platform.file.BackupFileWriter
    @Inject lateinit var reminderAlarmScheduler: com.kosmos.app.platform.alarm.ReminderAlarmScheduler
    @Inject lateinit var widgetRefresher: com.kosmos.app.widget.WidgetRefresher
    @Inject lateinit var getTodayScheduleUseCase: com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
    @Inject lateinit var summarizeScheduleUseCase: com.kosmos.app.domain.usecase.SummarizeScheduleUseCase
    @Inject lateinit var recentDocumentsStore: com.kosmos.app.data.local.prefs.RecentDocumentsStore
    @Inject lateinit var documentAccess: com.kosmos.app.platform.document.DocumentAccess
    @Inject lateinit var cleanupRunner: com.kosmos.app.assistant.cleanup.MemoryCleanupRunner
    @Inject lateinit var dataResetRepository: com.kosmos.app.domain.memory.DataResetRepository
    @Inject lateinit var reminderEditor: com.kosmos.app.feature.chat.result.ReminderEditor

    @BindValue @JvmField
    val tokenizer: com.kosmos.app.domain.tool.Tokenizer = object : com.kosmos.app.domain.tool.Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 2
    }

    @BindValue @JvmField
    val imageProcessor: com.kosmos.app.domain.tool.ImageProcessor = object : com.kosmos.app.domain.tool.ImageProcessor {
        override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> = AppResult.Success(rawBytes)
    }

    @BindValue @JvmField
    val modelLoadManager: com.kosmos.app.domain.modelrunner.ModelLoadManager = object : com.kosmos.app.domain.modelrunner.ModelLoadManager {
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(MODEL))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: ModelInfo) {}
    }

    @BindValue @JvmField
    val audioRecorder: com.kosmos.app.platform.speech.AudioRecorder =
        object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
            override fun startRecording(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun stopRecording(): AppResult<File> = AppResult.Success(File.createTempFile("t", "a"))
        }

    /** 일정 요청에만 툴 호출(승인 카드)로 답하고 나머지는 짧은 문장. */
    @BindValue @JvmField
    val modelRunner: ModelRunner = object : ModelRunner {
        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.Ready(MODEL))
        override suspend fun warmUp() {}
        override suspend fun generate(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            onToken: ((String) -> Unit)?
        ): AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> {
            val turn = if (prompt.toolResponse == null && prompt.currentInput.contains("회의")) {
                com.kosmos.app.domain.modelrunner.ModelTurn(
                    text = "",
                    toolCalls = listOf(
                        com.kosmos.app.domain.modelrunner.ModelToolCall(
                            name = "AddSchedule",
                            args = mapOf(
                                "title" to "팀 회의", "startTime" to "2026-10-19T10:00:00",
                                "endTime" to "2026-10-19T11:00:00", "description" to "다음주 월요일 팀 회의"
                            )
                        )
                    )
                )
            } else {
                com.kosmos.app.domain.modelrunner.ModelTurn("알겠어요.")
            }
            return AppResult.Success(turn)
        }
        override suspend fun generateWithImage(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, imageBytes: ByteArray, onToken: ((String) -> Unit)?
        ) = generate(prompt, onToken)
        override suspend fun generateWithAudio(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?
        ) = generate(prompt, onToken)
        override suspend fun cancel() {}
        override fun close() {}
    }

    private var themeMode by mutableStateOf(ThemeMode.LIGHT)
    private var chatTextSize = com.kosmos.app.ui.theme.ChatTextSize.DEFAULT
    private val defaultZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        // [WHY] 일정 승인은 화면이 캘린더 권한을 먼저 확인한다 — 테스트 앱에서는 거부로 처리돼 승인 카드가 안 뜬다.
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(
            android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.POST_NOTIFICATIONS
        )
        hiltRule.inject()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
    }

    // ── 가짜 데이터 — 실제 대화가 아니다 ──

    private suspend fun seed() {
        val now = NOW
        sessionStore.saveActiveSessionId(SESSION)
        val episode = Episode(
            id = "ep-bike", sessionId = SESSION, status = EpisodeStatus.SUMMARIZED, title = "자전거 비밀번호 확인",
            summary = "사용자가 자전거 비밀번호를 물었고 저장된 메모의 134 로 답했다.", tags = listOf("자전거", "비밀번호"),
            startAt = now - DAY * 2, endAt = now - DAY * 2 + 600_000, messageCount = 4, retryCount = 0,
            createdAt = now - DAY * 2, updatedAt = now - DAY * 2
        )
        episodeRepository.insert(episode)
        episodeRepository.insert(
            episode.copy(
                id = "ep-dentist", title = "치과 예약과 저녁 약속", summary = "내일 오후 3시 치과 예약을 잡고 금요일 저녁 약속을 정리했다.",
                tags = listOf("치과", "약속"), startAt = now - DAY * 5, endAt = now - DAY * 5 + 600_000, createdAt = now - DAY * 5
            )
        )
        val messages = listOf(
            msg("m1", ChatMessage.Role.ASSISTANT, "좋은 아침이에요. 오늘은 일정이 비어 있고, 할 일 2개가 남아 있어요. 어제 말씀하신 저녁 메뉴는 정하셨어요?", now - 3_600_000, InputType.BRIEFING),
            msg("m2", ChatMessage.Role.USER, "30분 뒤에 물 마시라고 알려줘", now - 1_800_000),
            msg("m3", ChatMessage.Role.ASSISTANT, "오후 1시 56분에 알려 드릴게요.", now - 1_790_000,
                result = MessageResult(MessageResult.Kind.REMINDER, "t1")),
            msg("m3b", ChatMessage.Role.USER, "다음 주 월요일 10시에 팀 회의 잡아줘", now - 1_200_000),
            msg("m3c", ChatMessage.Role.ASSISTANT, "10월 19일 월요일 오전 10시로 캘린더에 넣었어요.", now - 1_190_000,
                result = MessageResult(MessageResult.Kind.SCHEDULE, "t5")),
            msg("m4", ChatMessage.Role.USER, "자전거 비밀번호 기억나?", now - 600_000),
            msg("m5", ChatMessage.Role.ASSISTANT, "134예요. 예전 대화엔 4321도 있었는데, 직접 저장하신 메모가 134라서 그걸로 알려 드려요.", now - 590_000,
                recall = listOf("ep-bike"),
                result = MessageResult(MessageResult.Kind.MEMORY_RECALL, "k1", """{"noteAt":${now - DAY * 3},"conflictCount":1,"otherValues":["4321"]}""")),
            msg("m6", ChatMessage.Role.USER, "커피 하나 추천해 줘", now - 120_000),
            msg("m7", ChatMessage.Role.ASSISTANT, "오후니까 디카페인 라떼 어때요? 진한 쪽이 좋으세요, 부드러운 쪽이 좋으세요?", now - 110_000)
        )
        messages.forEach { conversationRepository.save(it) }
        knowledgeRepository.save(KnowledgeNote(id = "k1", content = "자전거 비밀번호는 134", tags = listOf("자전거", "비밀번호"), createdAt = now - DAY * 3, updatedAt = now - DAY * 3))
        knowledgeRepository.save(KnowledgeNote(id = "k2", content = "아침엔 커피 대신 녹차를 마심", tags = listOf("녹차"), createdAt = now - DAY, updatedAt = now - DAY, source = KnowledgeNote.SOURCE_AUTO))
        knowledgeRepository.save(KnowledgeNote(id = "k3", content = "회사 주차는 B2 구역 117번", tags = listOf("주차"), createdAt = now, updatedAt = now))
        taskRepository.save(TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = now - 1_800_000, remindAtIso = "2026-10-15T13:56:00"))
        taskRepository.save(TaskItem(id = "t2", title = "치과 예약 전화하기", isCompleted = false, createdAt = now - DAY))
        taskRepository.save(TaskItem(id = "t3", title = "장보기", isCompleted = true, createdAt = now - DAY * 2))
        // 일정 결과 카드(0.38.2)의 대상, 취소한 리마인더(할 일 탭에서는 숨겨진다 — 결정 1)
        taskRepository.save(TaskItem(id = "t5", title = "팀 회의", isCompleted = false, createdAt = now - 1_190_000,
            dueDateIso = "2026-10-19T10:00:00", endDateIso = "2026-10-19T11:00:00"))
        taskRepository.save(TaskItem(id = "t4", title = "스트레칭", isCompleted = false, createdAt = now - DAY,
            remindAtIso = "2026-10-15T15:40:00", cancelledAt = now - 3_000_000))
        profileRepository.upsert("호칭", "진우님")
        profileRepository.upsert("직업", "개발자")
        auditRepository.save(AuditEvent("a1", AuditEventType.TOOL_CALL, SESSION, "AddReminder 물 마시기 13:56", now - 1_790_000))
        auditRepository.save(AuditEvent("a2", AuditEventType.APPROVAL_GRANTED, SESSION, "AddReminder 승인", now - 1_789_000))
        auditRepository.save(AuditEvent("a3", AuditEventType.SEARCH_USED, SESSION, "search_memory 자전거 비밀번호", now - 595_000))
    }

    private fun msg(
        id: String, role: ChatMessage.Role, content: String, at: Long,
        type: InputType = InputType.TEXT, recall: List<String> = emptyList(), result: MessageResult? = null
    ) = ChatMessage(id = id, sessionId = SESSION, role = role, content = content, inputType = type, createdAt = at, recallEpisodeIds = recall, result = result)

    // ── 촬영 ──

    // [WHY] 승인 장면은 지연 작업을 앞당기지 않는다 — 승인 카드의 제한 시간(ApprovalCoordinator)까지 당겨져 자동 거절된다.
    private var fastForward = true

    private fun settle() {
        repeat(6) {
            if (fastForward) ShadowLooper.runUiThreadTasksIncludingDelayedTasks() else ShadowLooper.idleMainLooper()
            rule.waitForIdle()
            Thread.sleep(40)
        }
    }

    /** 라이트로 찍고 같은 화면을 다크로 바꿔 다시 찍는다. 떠 있는 대화상자·시트는 화면 위에 합성된다. */
    private fun shoot(name: String, onSurface: Boolean = false, before: () -> Unit = {}, content: @Composable () -> Unit) {
        // [WHY] 앱처럼 배경을 깐다 — 화면들은 투명하고 MainActivity 의 Surface(background)가 바닥을 칠한다. 드로어는 surface 색 시트 위에 있다.
        rule.setContent {
            CompositionLocalProvider(LocalClock provides CLOCK) {
                KosmosTheme(themeMode = themeMode, chatTextSize = chatTextSize) {
                    // [WHY] 바닥을 화면 전체에 깐다 — 시트만 있는 장면은 내용 크기가 0 이라 창 기본색(밝은 회색)이 비쳤다(impeccable 점검 P3).
                    androidx.compose.material3.Surface(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        color = if (onSurface) KosmosTheme.colors.surface else androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) { content() }
                }
            }
        }
        settle()
        before()
        settle()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            themeMode = mode
            settle()
            val tag = if (mode == ThemeMode.LIGHT) "light" else "dark"
            captureScreenRoboImage("src/test/screenshots/$name-$tag.png")
        }
    }

    private fun chatViewModel() = com.kosmos.app.feature.chat.ChatViewModel(
        context = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(),
        sessionStore = sessionStore,
        conversationRepository = conversationRepository,
        episodeRepository = episodeRepository,
        sendChatMessageUseCase = sendChatMessageUseCase,
        approvalCoordinator = approvalCoordinator,
        shareIntentHandler = shareIntentHandler,
        runtimeMetricsCollector = runtimeMetricsCollector,
        modelRunner = modelRunner,
        audioRecorder = audioRecorder,
        briefingGenerator = briefingGenerator,
        voiceLaunchHandler = com.kosmos.app.platform.launch.VoiceLaunchHandler(),
        suggestionRepository = suggestionRepository,
        suggestionResolver = io.mockk.mockk(relaxed = true),
        speechOutput = io.mockk.mockk(relaxed = true) {
            io.mockk.every { speakingMessageId } returns MutableStateFlow(null)
            io.mockk.coEvery { autoReadEnabled() } returns false
        }
    )

    private fun memoryViewModel() = com.kosmos.app.feature.memory.MemoryViewModel(
        knowledgeRepository = knowledgeRepository, taskRepository = taskRepository,
        exportMemoryUseCase = exportMemoryUseCase, importMemoryUseCase = importMemoryUseCase,
        backupFileWriter = backupFileWriter, reminderAlarmScheduler = reminderAlarmScheduler, widgetRefresher = widgetRefresher
    )

    // ── 화면 ──

    /** 결과 카드·요약 타일(0.38.2) — 셸(AppNavHost)이 하듯 ChatExtrasViewModel 을 붙인다. 시각은 고정([NOW]). */
    @Composable
    private fun ChatWithExtras(vm: com.kosmos.app.feature.chat.ChatViewModel, extras: com.kosmos.app.feature.chat.result.ChatExtrasViewModel) {
        val states by extras.results.collectAsState()
        val summary by extras.summary.collectAsState()
        com.kosmos.app.feature.chat.ChatScreen(
            viewModel = vm,
            resultStates = states,
            onEnsureResult = { extras.ensure(it, TODAY) },
            summary = summary,
            onRefreshSummary = { extras.refreshSummary(NOW, ZoneId.of(ZONE)) }
        )
    }

    private fun extras() = com.kosmos.app.feature.chat.result.ChatExtrasViewModel(taskRepository, knowledgeRepository, getTodayScheduleUseCase, reminderEditor)

    @Test fun chat() {
        val vm = chatViewModel()
        val extras = extras()
        shoot("01-chat") { ChatWithExtras(vm, extras) }
    }

    /** 결과 카드(알림·일정)가 보이게 위로 올린 채팅 — 역방향 목록이라 index 3(자전거 질문)을 바닥에 둔다. */
    @Test fun chatResults() {
        val vm = chatViewModel()
        val extras = extras()
        shoot("15-chat-results", before = {
            rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToIndex(3)
        }) { ChatWithExtras(vm, extras) }
    }

    /** 마지막 답이 충돌 있는 기억 카드 — 행동 칩 [과거 기록 정리][메모 보기](0.38.2 M6). */
    @Test fun chatActionChips() {
        kotlinx.coroutines.runBlocking {
            conversationRepository.save(msg("m8", ChatMessage.Role.USER, "자전거 비밀번호 다시 알려 줘", NOW - 60_000))
            conversationRepository.save(
                msg("m9", ChatMessage.Role.ASSISTANT, "134예요. 예전 대화 한 건엔 4321로 적혀 있어요.", NOW - 50_000,
                    result = MessageResult(MessageResult.Kind.MEMORY_RECALL, "k1", """{"noteAt":${NOW - DAY * 3},"conflictCount":1,"otherValues":["4321"],"conflictEpisodeIds":["ep-bike"]}"""))
            )
        }
        val vm = chatViewModel()
        val extras = extras()
        shoot("20-chat-action-chips") { ChatWithExtras(vm, extras) }
    }

    /** 채팅 상태 표시 모음(0.38.2 M7) — 응답 대기·답 쓰는 중·입력바 네 상태(마이크·보내기·응답 중단·녹음 중). 부품만 쌓아 한 장에. */
    @Test fun chatStates() {
        shoot("21-chat-states") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(vertical = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    com.kosmos.app.feature.chat.TypingIndicator()
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    com.kosmos.app.feature.chat.ChatBubbleAssistant(text = "오후 1시 56분에 알려 드릴")
                }
                val bar: @Composable (Boolean, Boolean, com.kosmos.app.platform.share.SharedInput?) -> Unit = { loading, recording, shared ->
                    com.kosmos.app.feature.chat.ChatInputBar(
                        isLoading = loading, isRecording = recording, sharedInput = shared, onClearSharedInput = {},
                        onSend = {}, onMicClick = {}, onAttachClick = {}
                    )
                }
                bar(false, false, null)
                bar(false, false, com.kosmos.app.platform.share.SharedInput.Text("30분 뒤에 물 마시라고 알려줘"))
                bar(true, false, null)
                bar(false, true, null)
            }
        }
    }

    /** 첫 화면(0.38.2 M7b) — 대화 기록이 하나도 없을 때(처음 쓸 때·초기화 후). 타임라인은 세션과 무관하다(ADR-022). */
    @Test fun chatEmpty() {
        kotlinx.coroutines.runBlocking { dataResetRepository.reset(setOf(com.kosmos.app.domain.memory.ResetTarget.CONVERSATIONS)) }
        val vm = chatViewModel()
        val extras = extras()
        shoot("22-chat-empty") { ChatWithExtras(vm, extras) }
    }

    /** 채팅 글자 크기 "아주 크게"(0.38.2) — 목록 안만 커지고 상단 바·타일·입력바는 그대로. */
    @Test fun chatLargeText() {
        chatTextSize = com.kosmos.app.ui.theme.ChatTextSize.XLARGE
        val vm = chatViewModel()
        val extras = extras()
        shoot("16-chat-large-text") { ChatWithExtras(vm, extras) }
    }

    /** 알림 카드를 누르면 — 수정 시트(0.38.2 M4). 시트만 그린다(시각 고정). */
    @Test fun reminderEditSheet() {
        val state = com.kosmos.app.feature.chat.result.ResultCardMapper.reminder(
            TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00"),
            TODAY, ZoneId.of(ZONE)
        )
        shoot("17-reminder-edit-sheet") {
            com.kosmos.app.feature.chat.result.ReminderEditSheet(
                state = state, initialHour = 13, initialMinute = 56, error = null,
                onSnooze = {}, onReschedule = { _, _ -> }, onCancelReminder = {}, onDismiss = {}
            )
        }
    }

    @Test fun chatApproval() {
        val vm = chatViewModel()
        val extras = extras()
        shoot("02-chat-approval", before = { fastForward = false; vm.sendMessage("다음주 월요일 10시 팀 회의 일정 추가해줘") }) {
            ChatWithExtras(vm, extras)
        }
    }

    @Test fun reminderApprovalSheet() {
        val request = com.kosmos.app.assistant.approval.ApprovalRequest(
            sessionId = SESSION, title = "리마인더 등록", description = "오늘 오후 1:56에 알림: '물 마시기'", showsNotification = true,
            preview = com.kosmos.app.assistant.approval.ApprovalPreview(
                com.kosmos.app.assistant.approval.ApprovalPreview.Kind.REMINDER, "물 마시기", atIso = "2026-10-15T13:56:00"
            )
        )
        shoot("03-approval-reminder") {
            com.kosmos.app.feature.approval.ApprovalSheet(
                request = request, onApprove = {}, onReject = {},
                footnote = com.kosmos.app.platform.notification.NotificationAccess.INEXACT_NOTE, onFootnoteAction = {}
            )
        }
    }

    @Test fun memoryApprovalSheet() {
        val request = com.kosmos.app.assistant.approval.ApprovalRequest(
            sessionId = SESSION, title = "메모리 저장 승인", description = "저장할 내용: 자전거 자물쇠 비밀번호는 현관 서랍 수첩에 적어 둠",
            preview = com.kosmos.app.assistant.approval.ApprovalPreview(
                com.kosmos.app.assistant.approval.ApprovalPreview.Kind.MEMORY, "자전거 자물쇠 비밀번호는 현관 서랍 수첩에 적어 둠"
            )
        )
        shoot("18-approval-memory") {
            com.kosmos.app.feature.approval.ApprovalSheet(request = request, onApprove = {}, onReject = {})
        }
    }

    @Test fun profileSuggestionSheet() {
        val suggestion = com.kosmos.app.domain.model.ProfileSuggestion(
            id = "ps1", key = "직업", value = "백엔드 개발자", episodeId = null,
            status = com.kosmos.app.domain.model.ProfileSuggestionStatus.PENDING, createdAt = 0, updatedAt = 0
        )
        shoot("19-profile-suggestion") {
            com.kosmos.app.feature.approval.ProfileSuggestionSheet(suggestion, remaining = 2, onAccept = {}, onReject = {}, onLater = {})
        }
    }

    @Test fun drawer() {
        val vm = com.kosmos.app.feature.drawer.DrawerViewModel(episodeRepository, profileRepository, suggestionRepository)
        shoot("04-drawer", onSurface = true) { com.kosmos.app.feature.drawer.AppDrawerContent(viewModel = vm) }
    }

    @Test fun memoryNotes() {
        val vm = memoryViewModel()
        shoot("05-memory-notes") { com.kosmos.app.feature.memory.MemoryScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun memoryTasks() {
        val vm = memoryViewModel()
        shoot("06-memory-tasks", before = {
            vm.onFilterSelected(com.kosmos.app.feature.memory.MemoryFilterType.TASK)
            settle()
            runCatching { rule.onNodeWithText("펼치기").performClick() }
        }) { com.kosmos.app.feature.memory.MemoryScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun calendar() {
        val vm = com.kosmos.app.feature.calendar.CalendarViewModel(getTodayScheduleUseCase, summarizeScheduleUseCase)
        shoot("07-calendar", before = { vm.selectMonthDate(TODAY) }) { com.kosmos.app.feature.calendar.CalendarScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun documentHome() {
        val vm = com.kosmos.app.feature.document.DocumentHomeViewModel(recentDocumentsStore, documentAccess)
        shoot("08-documents") { com.kosmos.app.feature.document.DocumentHomeScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun settings() {
        val vm = com.kosmos.app.feature.settings.SettingsViewModel(
            settingsDataStore, runtimeManager, modelRunner, briefingScheduler, io.mockk.mockk(relaxed = true)
        )
        val themeVm = com.kosmos.app.ui.theme.ThemeViewModel(settingsDataStore)
        val resetVm = com.kosmos.app.feature.settings.DataResetViewModel(
            dataResetRepository, taskRepository, reminderAlarmScheduler, widgetRefresher, sessionStore
        )
        shoot("09-settings") {
            com.kosmos.app.feature.settings.SettingsScreen(viewModel = vm, themeViewModel = themeVm, dataResetViewModel = resetVm, onBack = {})
        }
    }

    @Test fun memoryCleanup() {
        val vm = com.kosmos.app.feature.cleanup.MemoryCleanupViewModel(cleanupRunner)
        shoot("10-memory-cleanup") { com.kosmos.app.feature.cleanup.MemoryCleanupScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun profileSheet() {
        val vm = com.kosmos.app.feature.profile.ProfileSheetViewModel(profileRepository, tokenizer)
        shoot("11-profile-sheet") { com.kosmos.app.feature.profile.ProfileSheet(onDismiss = {}, viewModel = vm) }
    }

    @Test fun episodeSheet() {
        val vm = com.kosmos.app.feature.episode.EpisodeSheetViewModel(episodeRepository)
        shoot("12-episode-sheet") { com.kosmos.app.feature.episode.EpisodeSheet(episodeId = "ep-bike", onDismiss = {}, viewModel = vm) }
    }

    @Test fun audit() {
        val vm = com.kosmos.app.feature.settings.AuditViewModel(auditRepository)
        shoot("13-audit") { com.kosmos.app.feature.settings.AuditScreen(viewModel = vm, onNavigateBack = {}) }
    }

    @Test fun splash() {
        val vm = com.kosmos.app.feature.splash.SplashViewModel(
            object : ModelRunner by modelRunner {
                override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.InitializingEngine)
            }
        )
        shoot("14-splash") { com.kosmos.app.feature.splash.SplashScreen(viewModel = vm, onInitializationComplete = {}) }
    }

    private companion object {
        const val SESSION = "s-catalog"
        const val DAY = 86_400_000L
        const val ZONE = "Asia/Seoul"
        val TODAY: LocalDate = LocalDate.of(2026, 10, 15)
        val NOW: Long = ZonedDateTime.of(TODAY.atTime(13, 30), ZoneId.of(ZONE)).toInstant().toEpochMilli()
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(ZONE))
        val MODEL = ModelInfo("mock", "mock", "1.0", "Q4", 0L)
    }
}
