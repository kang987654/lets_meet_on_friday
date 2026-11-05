package com.kosmos.app.design

import com.kosmos.app.domain.library.LibraryFailure
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.platform.document.LibraryIndexer
import com.kosmos.app.testing.FakeLibraryRepository
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import com.kosmos.app.R
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.approval.ApprovalPreview
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.assistant.briefing.MorningBriefingGenerator
import com.kosmos.app.assistant.cleanup.MemoryCleanupRunner
import com.kosmos.app.core.common.AppError
import com.kosmos.app.data.local.prefs.RecentDocumentsStore
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.di.ModelModule
import com.kosmos.app.domain.cleanup.MergeProposal
import com.kosmos.app.domain.document.CellStyle
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.SheetCell
import com.kosmos.app.domain.document.SheetRow
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.DataResetRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.ImageProcessor
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.usecase.DownloadModelUseCase
import com.kosmos.app.domain.usecase.ExportMemoryUseCase
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.domain.usecase.ImportMemoryUseCase
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import com.kosmos.app.feature.approval.ApprovalSheet
import com.kosmos.app.feature.approval.ProfileSuggestionSheet
import com.kosmos.app.feature.calendar.CalendarScreen
import com.kosmos.app.feature.calendar.CalendarTab
import com.kosmos.app.feature.calendar.CalendarViewModel
import com.kosmos.app.feature.chat.ChatBubbleAssistant
import com.kosmos.app.feature.chat.ChatInputBar
import com.kosmos.app.feature.chat.ChatScreen
import com.kosmos.app.feature.chat.ChatViewModel
import com.kosmos.app.feature.chat.CustomChatHeader
import com.kosmos.app.feature.chat.StatusDetailContent
import com.kosmos.app.feature.chat.TypingIndicator
import com.kosmos.app.feature.chat.result.ChatExtrasViewModel
import com.kosmos.app.feature.chat.result.ReminderEditSheet
import com.kosmos.app.feature.chat.result.ReminderEditor
import com.kosmos.app.feature.chat.result.ResultCardMapper
import com.kosmos.app.feature.cleanup.MemoryCleanupScreen
import com.kosmos.app.feature.cleanup.MemoryCleanupViewModel
import com.kosmos.app.feature.document.DocumentHomeScreen
import com.kosmos.app.feature.document.DocumentHomeViewModel
import com.kosmos.app.feature.document.DocumentViewerScreen
import com.kosmos.app.feature.document.DocumentViewerState
import com.kosmos.app.feature.drawer.AppDrawerContent
import com.kosmos.app.feature.drawer.DrawerViewModel
import com.kosmos.app.feature.episode.EpisodeSheet
import com.kosmos.app.feature.episode.EpisodeSheetViewModel
import com.kosmos.app.feature.memory.MemoryFilterType
import com.kosmos.app.feature.memory.MemoryScreen
import com.kosmos.app.feature.memory.MemoryViewModel
import com.kosmos.app.feature.profile.ProfileSheet
import com.kosmos.app.feature.profile.ProfileSheetViewModel
import com.kosmos.app.feature.settings.AuditScreen
import com.kosmos.app.feature.settings.AuditViewModel
import com.kosmos.app.feature.settings.DataResetViewModel
import com.kosmos.app.feature.settings.ModelManagementScreen
import com.kosmos.app.feature.settings.ModelManagementViewModel
import com.kosmos.app.feature.settings.SettingsScreen
import com.kosmos.app.feature.settings.SettingsViewModel
import com.kosmos.app.feature.splash.SplashScreen
import com.kosmos.app.feature.splash.SplashViewModel
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import com.kosmos.app.platform.document.DocumentAccess
import com.kosmos.app.platform.file.BackupFileWriter
import com.kosmos.app.platform.launch.VoiceLaunchHandler
import com.kosmos.app.platform.notification.NotificationAccess
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.share.SharedInput
import com.kosmos.app.platform.speech.AudioRecorder
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import com.kosmos.app.runtime.metrics.DeviceStatus
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.ui.component.DeleteIconButton
import com.kosmos.app.ui.component.EmptyState
import com.kosmos.app.ui.component.KosmosListGroup
import com.kosmos.app.ui.component.KosmosListRow
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.theme.ChatTextSize
import com.kosmos.app.ui.theme.SplashStyle
import com.kosmos.app.ui.theme.ThemeViewModel
import com.kosmos.app.widget.WidgetRefresher
import com.kosmos.app.work.BriefingNotificationScheduler

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
@UninstallModules(ModelModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "ko-rKR-w393dp-h852dp-xxhdpi")
class ScreenCatalogTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var episodeRepository: EpisodeRepository
    @Inject lateinit var knowledgeRepository: KnowledgeRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var suggestionRepository: ProfileSuggestionRepository
    @Inject lateinit var auditRepository: AuditRepository
    @Inject lateinit var briefingGenerator: MorningBriefingGenerator
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var runtimeManager: GemmaRuntimeManager
    @Inject lateinit var briefingScheduler: BriefingNotificationScheduler
    @Inject lateinit var exportMemoryUseCase: ExportMemoryUseCase
    @Inject lateinit var importMemoryUseCase: ImportMemoryUseCase
    @Inject lateinit var backupFileWriter: BackupFileWriter
    @Inject lateinit var reminderAlarmScheduler: ReminderAlarmScheduler
    @Inject lateinit var widgetRefresher: WidgetRefresher
    @Inject lateinit var getTodayScheduleUseCase: GetTodayScheduleUseCase
    @Inject lateinit var recentDocumentsStore: RecentDocumentsStore
    @Inject lateinit var documentAccess: DocumentAccess
    @Inject lateinit var cleanupRunner: MemoryCleanupRunner
    @Inject lateinit var dataResetRepository: DataResetRepository
    @Inject lateinit var reminderEditor: ReminderEditor

    @BindValue @JvmField
    val tokenizer: Tokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 2
    }

    @BindValue @JvmField
    val imageProcessor: ImageProcessor = object : ImageProcessor {
        override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> = AppResult.Success(rawBytes)
    }

    @BindValue @JvmField
    val modelLoadManager: ModelLoadManager = object : ModelLoadManager {
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(MODEL))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: ModelInfo) {}
    }

    @BindValue @JvmField
    val audioRecorder: AudioRecorder =
        object : AudioRecorder(ApplicationProvider.getApplicationContext()) {
            override fun startRecording(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun stopRecording(): AppResult<File> = AppResult.Success(File.createTempFile("t", "a"))
        }

    /** 일정 요청에만 툴 호출(승인 카드)로 답하고 나머지는 짧은 문장. */
    @BindValue @JvmField
    val modelRunner: ModelRunner = object : ModelRunner {
        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.Ready(MODEL))
        override suspend fun warmUp() {}
        override suspend fun generate(
            prompt: ChatPrompt,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> {
            val turn = if (prompt.toolResponse == null && prompt.currentInput.contains("회의")) {
                ModelTurn(
                    text = "",
                    toolCalls = listOf(
                        ModelToolCall(
                            name = "AddSchedule",
                            args = mapOf(
                                "title" to "팀 회의", "startTime" to "2026-10-19T10:00:00",
                                "endTime" to "2026-10-19T11:00:00", "description" to "다음주 월요일 팀 회의"
                            )
                        )
                    )
                )
            } else {
                ModelTurn("알겠어요.")
            }
            return AppResult.Success(turn)
        }
        override suspend fun generateWithImage(
            prompt: ChatPrompt, imageBytes: ByteArray, onToken: ((String) -> Unit)?
        ) = generate(prompt, onToken)
        override suspend fun generateWithAudio(
            prompt: ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?
        ) = generate(prompt, onToken)
        override suspend fun cancel() {}
        override fun close() {}
    }

    private var themeMode by mutableStateOf(ThemeMode.LIGHT)
    private var chatTextSize = ChatTextSize.DEFAULT
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
            summary = "사용자가 자전거 비밀번호를 물었고, 비서가 저장된 메모의 134로 답했어요.", tags = listOf("자전거", "비밀번호"),
            startAt = now - DAY * 2, endAt = now - DAY * 2 + 600_000, messageCount = 4, retryCount = 0,
            createdAt = now - DAY * 2, updatedAt = now - DAY * 2
        )
        episodeRepository.insert(episode)
        episodeRepository.insert(
            episode.copy(
                id = "ep-dentist", title = "치과 예약과 저녁 약속", summary = "사용자가 내일 오후 3시 치과 예약을 잡고 금요일 저녁 약속을 정리했어요.",
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
        // 기록 원문은 AuditTrailService 형식 그대로(0.38.3 M3) — 화면이 이 형식을 한국어 표기로 바꾼다.
        auditRepository.save(AuditEvent("a0", AuditEventType.APPROVAL_REJECTED, SESSION, "AddSchedule: 팀 회의 10월 19일 오전 10:00", now - 90_000_000))
        auditRepository.save(AuditEvent("a2", AuditEventType.APPROVAL_GRANTED, SESSION, "AddReminder: 물 마시기 오후 1:56", now - 1_790_000))
        auditRepository.save(AuditEvent("a1", AuditEventType.TOOL_CALL, SESSION, "Tool: AddReminder\nResult: {\"ok\":true,\"title\":\"물 마시기\"}", now - 1_789_000))
        auditRepository.save(AuditEvent("a3", AuditEventType.TOOL_CALL, SESSION, "Tool: SearchMemory\nResult: {\"hits\":1}", now - 595_000))
        auditRepository.save(AuditEvent("a4", AuditEventType.MODEL_RUN, SESSION, "Prompt: 자전거 비밀번호 뭐였지?\nOutput: 134예요. 예전 대화엔 4321도 있었는데, 직접 저장하신 메모가 134라서 그걸로 알려 드려요.\nTools declared: [AddSchedule, GetSchedule, AddMemory, SearchMemory, AddReminder]", now - 590_000))
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

    private fun chatViewModel() = ChatViewModel(
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
        voiceLaunchHandler = VoiceLaunchHandler(),
        suggestionRepository = suggestionRepository,
        suggestionResolver = io.mockk.mockk(relaxed = true),
        speechOutput = io.mockk.mockk(relaxed = true) {
            io.mockk.every { speakingMessageId } returns MutableStateFlow(null)
            io.mockk.coEvery { autoReadEnabled() } returns false
        },
        conversationPrewarmer = io.mockk.mockk(relaxed = true)
    )

    private fun memoryViewModel() = MemoryViewModel(
        knowledgeRepository = knowledgeRepository, taskRepository = taskRepository,
        exportMemoryUseCase = exportMemoryUseCase, importMemoryUseCase = importMemoryUseCase,
        backupFileWriter = backupFileWriter, reminderAlarmScheduler = reminderAlarmScheduler, widgetRefresher = widgetRefresher
    )

    // ── 화면 ──

    /** 결과 카드·요약 타일(0.38.2) — 셸(AppNavHost)이 하듯 ChatExtrasViewModel 을 붙인다. 시각은 고정([NOW]). */
    @Composable
    private fun ChatWithExtras(vm: ChatViewModel, extras: ChatExtrasViewModel) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val states by extras.results.collectAsState()
        val summary by extras.summary.collectAsState()
        ChatScreen(
            viewModel = vm,
            resultStates = states,
            onEnsureResult = { extras.ensure(context, it, TODAY) },
            summary = summary,
            onRefreshSummary = { extras.refreshSummary(NOW, ZoneId.of(ZONE)) }
        )
    }

    private fun extras() = ChatExtrasViewModel(taskRepository, knowledgeRepository, getTodayScheduleUseCase, reminderEditor)

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

    /** 마지막 답이 충돌 있는 기억 카드 — 행동 칩 [지난 대화 정리][메모 보기](0.38.2 M6). */
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

    /** 지난 대화만 근거인 기억 카드 — 카드·[대화 보기]가 대화 시트를 연다(0.38.6 M3). */
    @Test fun chatRecallEpisode() {
        kotlinx.coroutines.runBlocking {
            conversationRepository.save(msg("m8", ChatMessage.Role.USER, "지난번에 자전거 비밀번호 뭐로 바꿨지?", NOW - 60_000))
            conversationRepository.save(
                msg("m9", ChatMessage.Role.ASSISTANT, "지난 대화에서는 4321로 바꿨다고 하셨어요.", NOW - 50_000, recall = listOf("ep-bike"),
                    result = MessageResult(MessageResult.Kind.MEMORY_RECALL, "", """{"episodeId":"ep-bike"}"""))
            )
        }
        val vm = chatViewModel()
        val extras = extras()
        shoot("35-chat-recall-episode") { ChatWithExtras(vm, extras) }
    }

    /** 문서 검색 답의 출처 카드(0.41.2 M4) — 첫 출처는 제목·문서, 나머지는 근거 줄. 누르면 뷰어를 그 위치로 연다. */
    @Test fun chatDocSource() {
        kotlinx.coroutines.runBlocking {
            conversationRepository.save(msg("m8", ChatMessage.Role.USER, "돈을 빌려주고 오래 안 받으면 언제 못 받게 돼?", NOW - 60_000))
            conversationRepository.save(
                msg("m9", ChatMessage.Role.ASSISTANT, "민법 제162조에 따르면 빌려준 돈(채권)은 10년 동안 청구하지 않으면 소멸시효가 완성돼 받을 수 없게 돼요.", NOW - 50_000,
                    result = MessageResult(
                        MessageResult.Kind.DOC_SOURCE, "d-civil",
                        """{"sources":[{"d":"d-civil","n":"민법","s":"제162조(채권, 재산권의 소멸시효)","i":161},""" +
                            """{"d":"d-civil","n":"민법","s":"제163조(3년의 단기소멸시효)","i":162},{"d":"d-civil","n":"민법","s":"제164조(1년의 단기소멸시효)","i":163}]}"""
                    ))
            )
        }
        val vm = chatViewModel()
        val extras = extras()
        shoot("41-chat-doc-source") { ChatWithExtras(vm, extras) }
    }

    /** 입력바 검색 팝업(0.41.2 M4) — 웹 검색 꺼짐·문서 검색 켜짐, 그리고 보관함이 빈 때의 비활성 줄. */
    @Test fun chatSearchMenu() {
        shoot("40-chat-search-menu", before = {
            rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("검색 설정"))[0].performClick()
        }) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
            ) {
                ChatInputBar(
                    isLoading = false, isRecording = false, sharedInput = null, onClearSharedInput = {},
                    onSend = {}, onMicClick = {}, onAttachClick = {},
                    docSearchEnabled = true, docSearchAvailable = true
                )
                ChatInputBar(
                    isLoading = false, isRecording = false, sharedInput = null, onClearSharedInput = {},
                    onSend = {}, onMicClick = {}, onAttachClick = {}
                )
            }
        }
    }

    /** 채팅 상태 표시 모음(0.38.2 M7) — 응답 대기·답 쓰는 중·입력바 네 상태(마이크·보내기·답 멈추기·녹음 중). 부품만 쌓아 한 장에. */
    @Test fun chatStates() {
        shoot("21-chat-states") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(vertical = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    TypingIndicator()
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    ChatBubbleAssistant(text = "오후 1시 56분에 알려 드릴")
                }
                val bar: @Composable (Boolean, Boolean, SharedInput?) -> Unit = { loading, recording, shared ->
                    ChatInputBar(
                        isLoading = loading, isRecording = recording, sharedInput = shared, onClearSharedInput = {},
                        onSend = {}, onMicClick = {}, onAttachClick = {}
                    )
                }
                bar(false, false, null)
                bar(false, false, SharedInput.Text("30분 뒤에 물 마시라고 알려줘"))
                bar(true, false, null)
                bar(false, true, null)
            }
        }
    }

    /** 헤더 별·캡슐 네 상태와 엔진 오류 때의 상태 시트 본문(0.39.3 M3) — 실기기에서 발열·엔진 오류를 일부러 만들기 어려워 여기서만 본다. */
    @Test fun chatHeaderStates() {
        val warm = DeviceStatus(temperatureCelsius = 43f, appMemoryBytes = 6_500_000_000L)
        val error = ModelLoadState.Error(AppError.ModelLoadFailed("x"))
        shoot("37-chat-header-states") {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                CustomChatHeader(engineState = ModelLoadState.Ready(MODEL), deviceStatus = warm.copy(temperatureCelsius = 36f))
                CustomChatHeader(engineState = ModelLoadState.Ready(MODEL), deviceStatus = warm, warningMessage = "기기가 따뜻해져 답이 조금 늦을 수 있어요.")
                CustomChatHeader(engineState = error, deviceStatus = warm.copy(temperatureCelsius = 36f))
                CustomChatHeader(engineState = ModelLoadState.NotFound("/m"), deviceStatus = warm.copy(temperatureCelsius = 36f))
                StatusDetailContent(
                    engineState = error,
                    status = warm.copy(temperatureCelsius = 36.2f, memoryUsedBytes = 9_100_000_000L, memoryTotalBytes = 11_200_000_000L),
                    warningMessage = null,
                    onRetry = {}
                )
            }
        }
    }

    /** 첫 화면(0.38.2 M7b) — 대화 기록이 하나도 없을 때(처음 쓸 때·초기화 후). 타임라인은 세션과 무관하다(ADR-022). */
    @Test fun chatEmpty() {
        kotlinx.coroutines.runBlocking { dataResetRepository.reset(setOf(ResetTarget.CONVERSATIONS)) }
        val vm = chatViewModel()
        val extras = extras()
        shoot("22-chat-empty") { ChatWithExtras(vm, extras) }
    }

    /** 채팅 글자 크기 "아주 크게"(0.38.2) — 목록 안만 커지고 상단 바·타일·입력바는 그대로. */
    @Test fun chatLargeText() {
        chatTextSize = ChatTextSize.XLARGE
        val vm = chatViewModel()
        val extras = extras()
        shoot("16-chat-large-text") { ChatWithExtras(vm, extras) }
    }

    /** 알림 카드를 누르면 — 수정 시트(0.38.2 M4). 시트만 그린다(시각 고정). */
    @Test fun reminderEditSheet() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val state = ResultCardMapper.reminder(
            context,
            TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00"),
            TODAY, ZoneId.of(ZONE)
        )
        shoot("17-reminder-edit-sheet") {
            ReminderEditSheet(
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
        val request = ApprovalRequest(
            sessionId = SESSION, title = "알림 등록", description = "오늘 오후 1:56에 알림: '물 마시기'", showsNotification = true,
            preview = ApprovalPreview(
                ApprovalPreview.Kind.REMINDER, "물 마시기", atIso = "2026-10-15T13:56:00"
            )
        )
        shoot("03-approval-reminder") {
            ApprovalSheet(
                request = request, onApprove = {}, onReject = {},
                footnote = NotificationAccess.inexactNote(ApplicationProvider.getApplicationContext()), onFootnoteAction = {}
            )
        }
    }

    @Test fun memoryApprovalSheet() {
        val request = ApprovalRequest(
            sessionId = SESSION, title = "메모 저장", description = "저장할 내용: 자전거 자물쇠 비밀번호는 현관 서랍 수첩에 적어 둠",
            preview = ApprovalPreview(
                ApprovalPreview.Kind.MEMORY, "자전거 자물쇠 비밀번호는 현관 서랍 수첩에 적어 둠"
            )
        )
        shoot("18-approval-memory") {
            ApprovalSheet(request = request, onApprove = {}, onReject = {})
        }
    }

    @Test fun profileSuggestionSheet() {
        fun suggestion(id: String, key: String, value: String) = ProfileSuggestion(
            id = id, key = key, value = value, episodeId = null,
            status = ProfileSuggestionStatus.PENDING, createdAt = 0, updatedAt = 0
        )
        val suggestions = listOf(
            suggestion("ps1", "직업", "백엔드 개발자"),
            suggestion("ps2", "사는 곳", "대전"),
            suggestion("ps3", "좋아하는 음료", "녹차")
        )
        shoot("19-profile-suggestion") {
            ProfileSuggestionSheet(suggestions, onSave = {}, onRejectAll = {}, onLater = {})
        }
    }

    @Test fun drawer() {
        val vm = DrawerViewModel(episodeRepository, suggestionRepository)
        shoot("04-drawer", onSurface = true) { AppDrawerContent(viewModel = vm) }
    }

    @Test fun memoryNotes() {
        val vm = memoryViewModel()
        shoot("05-memory-notes") { MemoryScreen(viewModel = vm, onBack = {}) }
    }

    /** 메모 화면 ⋮ 메뉴 — 백업 내보내기·가져오기(0.38.3 M2 — 목록 아래 버튼에서 상단 바 메뉴로). */
    @Test fun memoryBackupMenu() {
        val vm = memoryViewModel()
        shoot("29-memory-backup-menu", before = {
            rule.onNode(androidx.compose.ui.test.hasContentDescription("더보기")).performClick()
        }) { MemoryScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun memoryTasks() {
        val vm = memoryViewModel()
        shoot("06-memory-tasks", before = {
            vm.onFilterSelected(MemoryFilterType.TASK)
            settle()
            runCatching { rule.onNodeWithText("펼치기").performClick() }
        }) { MemoryScreen(viewModel = vm, onBack = {}) }
    }

    /** 일정 "오늘" 탭(0.38.3 M1 — 기본 탭). */
    @Test fun calendar() {
        val vm = calendarViewModel()
        shoot("07-calendar") { CalendarScreen(viewModel = vm, onBack = {}) }
    }

    /** 일정 월 탭(0.38.3 M1 — 기본 탭이 "오늘"이 되어 따로 찍는다). */
    @Test fun calendarMonth() {
        val vm = calendarViewModel()
        shoot("27-calendar-month", before = {
            vm.onTabSelected(CalendarTab.MONTH)
            vm.selectMonthDate(TODAY)
        }) { CalendarScreen(viewModel = vm, onBack = {}) }
    }

    private fun calendarViewModel() = CalendarViewModel(
        getTodayScheduleUseCase, taskRepository, reminderEditor, io.mockk.mockk(relaxed = true)
    )

    @Test fun documentHome() {
        val vm = DocumentHomeViewModel(recentDocumentsStore, documentAccess, FakeLibraryRepository(), io.mockk.mockk<LibraryIndexer>(relaxed = true) { io.mockk.every { progress } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }, com.kosmos.app.platform.document.LibraryEmbedder(FakeLibraryRepository()) { null }, { null })
        shoot("08-documents") { DocumentHomeScreen(viewModel = vm, onBack = {}) }
    }

    /** 최근 문서가 있는 문서 홈 — 형식 아이콘·연 날짜·휴지통(0.38.3 M4). */
    @Test fun documentHomeRecent() {
        runBlocking {
            listOf(
                RecentDocument("content://d/4", "주민센터 신청서.hwpx", null, NOW - 6 * 86_400_000L),
                RecentDocument("content://d/3", "회의록 10월 2주차.docx", null, NOW - 86_400_000L - 3_600_000L),
                RecentDocument("content://d/2", "임대차 계약서.pdf", "application/pdf", NOW - 86_400_000L),
                RecentDocument("content://d/1", "가계부.xlsx", null, NOW - 1_200_000L)
            ).forEach { recentDocumentsStore.add(it) }
        }
        val vm = DocumentHomeViewModel(recentDocumentsStore, documentAccess, FakeLibraryRepository(), io.mockk.mockk<LibraryIndexer>(relaxed = true) { io.mockk.every { progress } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }, com.kosmos.app.platform.document.LibraryEmbedder(FakeLibraryRepository()) { null }, { null })
        shoot("31-documents-recent") { DocumentHomeScreen(viewModel = vm, onBack = {}) }
    }

    /** 문서 보관함 — 준비됨(조각·글자 수)·색인 중(쪽 진행률)·실패(사유 + 다시 시도) 세 상태(0.41.0). */
    @Test fun documentHomeLibrary() {
        val library = FakeLibraryRepository(
            listOf(
                LibraryDocument("l1", "content://l/1", "민법.pdf", "application/pdf", NOW - 3, LibraryStatus.READY, chunkCount = 1115, charCount = 155806),
                LibraryDocument("l2", "content://l/2", "출장 여비 지침.hwpx", null, NOW - 2, LibraryStatus.INDEXING),
                LibraryDocument("l3", "content://l/3", "스캔 계약서.pdf", "application/pdf", NOW - 1, LibraryStatus.FAILED, error = LibraryFailure.NO_TEXT.name)
            )
        )
        val indexer = io.mockk.mockk<LibraryIndexer>(relaxed = true) {
            io.mockk.every { progress } returns kotlinx.coroutines.flow.MutableStateFlow(mapOf("l2" to LibraryIndexer.Progress(11, 40)))
        }
        val vm = DocumentHomeViewModel(recentDocumentsStore, documentAccess, library, indexer, com.kosmos.app.platform.document.LibraryEmbedder(library) { null }, { null })
        shoot("34-documents-library") { DocumentHomeScreen(viewModel = vm, onBack = {}) }
    }

    /** 문서 보관함 뜻 검색 준비 줄 — 모델이 있고 벡터가 모자란 문서가 있을 때(0.41.2). */
    @Test fun documentHomeSemanticPending() {
        val library = FakeLibraryRepository(
            listOf(LibraryDocument("l1", "content://l/1", "민법.pdf", "application/pdf", NOW - 3, LibraryStatus.READY, chunkCount = 1115, charCount = 155806))
        ).apply { pendingInputs += com.kosmos.app.domain.library.LibraryVectorInput(1, "l1", "title: 민법 | text: 1") }
        val embedder = object : com.kosmos.app.domain.library.TextEmbedder {
            override val modelId = "eg2"
            override suspend fun embedQuery(query: String): FloatArray? = null
            override suspend fun embedDocuments(inputs: List<String>): List<FloatArray>? = null
        }
        val indexer = io.mockk.mockk<LibraryIndexer>(relaxed = true) { io.mockk.every { progress } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }
        // [WHY] 준비 버튼을 누르지 않은 상태 — 실행기에는 모델이 없는 것처럼 둬 촬영 중 일이 시작되지 않게 한다.
        val vm = DocumentHomeViewModel(recentDocumentsStore, documentAccess, library, indexer, com.kosmos.app.platform.document.LibraryEmbedder(library) { null }, { embedder })
        shoot("38-documents-semantic-pending") { DocumentHomeScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun settings() {
        val vm = SettingsViewModel(
            settingsDataStore, modelRunner, briefingScheduler, io.mockk.mockk(relaxed = true)
        )
        val themeVm = ThemeViewModel(settingsDataStore)
        val resetVm = DataResetViewModel(
            dataResetRepository, taskRepository, reminderAlarmScheduler, widgetRefresher, sessionStore
        )
        shoot("09-settings") {
            SettingsScreen(viewModel = vm, themeViewModel = themeVm, dataResetViewModel = resetVm, onBack = {})
        }
    }

    /** 설정 아래쪽 — 브리핑·기억·음성·데이터 묶음과 각주(0.38.3 M3, 첫 화면에는 위 세 묶음만 보인다). */
    @Test fun settingsLower() {
        val vm = SettingsViewModel(
            settingsDataStore, modelRunner, briefingScheduler, io.mockk.mockk(relaxed = true)
        )
        val themeVm = ThemeViewModel(settingsDataStore)
        val resetVm = DataResetViewModel(
            dataResetRepository, taskRepository, reminderAlarmScheduler, widgetRefresher, sessionStore
        )
        shoot("30-settings-lower", before = {
            rule.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput { swipeUp(); swipeUp(); swipeUp() }
        }) {
            SettingsScreen(viewModel = vm, themeViewModel = themeVm, dataResetViewModel = resetVm, onBack = {})
        }
    }

    @Test fun memoryCleanup() {
        val vm = MemoryCleanupViewModel(cleanupRunner)
        shoot("10-memory-cleanup") { MemoryCleanupScreen(viewModel = vm, onBack = {}) }
    }

    @Test fun profileSheet() {
        val vm = ProfileSheetViewModel(profileRepository, tokenizer, androidx.test.core.app.ApplicationProvider.getApplicationContext())
        shoot("11-profile-sheet") { ProfileSheet(onDismiss = {}, viewModel = vm) }
    }

    @Test fun episodeSheet() {
        val vm = EpisodeSheetViewModel(episodeRepository)
        shoot("12-episode-sheet") { EpisodeSheet(episodeId = "ep-bike", onDismiss = {}, viewModel = vm) }
    }

    @Test fun audit() {
        val vm = AuditViewModel(auditRepository)
        shoot("13-audit") { AuditScreen(viewModel = vm, onNavigateBack = {}) }
    }

    /** 활동 기록 체인이 깨졌을 때(0.41.4) — 목록 위 경고 띠. 목록은 그대로, 검증 결과만 "3번에서 내용 바뀜"으로 돌려준다. */
    @Test fun auditChainBroken() {
        val broken = object : AuditRepository by auditRepository {
            override suspend fun verifyChain() = AppResult.Success(
                com.kosmos.app.domain.audit.AuditHashChain.Result.Tampered(3, "a1", com.kosmos.app.domain.audit.AuditHashChain.Reason.HASH_MISMATCH)
            )
        }
        val vm = AuditViewModel(broken)
        shoot("42-audit-chain-broken") { AuditScreen(viewModel = vm, onNavigateBack = {}) }
    }

    /** 녹음 중 입력바 — 소리 크기가 흐르는 파형(0.38.4 M5, 사용자 선택 B). 크기는 고정 값을 시간에 따라 바꿔 넣는다. */
    @Test fun chatRecording() {
        var t = 0
        shoot("33-chat-recording") {
            Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                ChatInputBar(
                    isLoading = false, isRecording = true, sharedInput = null, onClearSharedInput = {},
                    onSend = {}, onMicClick = {}, onAttachClick = {},
                    recordingLevel = { t++; listOf(0.1f, 0.5f, 0.8f, 0.6f, 0.3f, 0.9f, 0.4f, 0.05f)[t % 8] }
                )
            }
        }
    }

    /** 활동 기록 펼침 — 물은 말·답·쓸 수 있던 도구, 그 아래 "원문 보기"(0.38.4 M2). */
    @Test fun auditExpanded() {
        val vm = AuditViewModel(auditRepository)
        shoot("32-audit-expanded", before = {
            rule.onNodeWithText("모델 답").performClick()
        }) { AuditScreen(viewModel = vm, onNavigateBack = {}) }
    }

    /** 기억 정리 검토 — 회고·합칠 기억·이어지는 대화(0.38.3 M2 — 시작 화면만 있던 장면에 검토 상태를 더한다). */
    @Test fun memoryCleanupReview() {
        fun note(id: String, content: String) = KnowledgeNote(id = id, content = content, createdAt = NOW, updatedAt = NOW)
        val review = MemoryCleanupRunner.State.Review(
            note("weekly", "이번 주는 치과 예약과 자전거 비밀번호 정리에 시간을 썼어요."),
            listOf(MergeProposal(listOf(note("c", "자전거 자물쇠 번호 4821"), note("d", "자전거 자물쇠 비밀번호는 4821")), "자전거 자물쇠 비밀번호는 4821"))
        )
        val runner = io.mockk.mockk<MemoryCleanupRunner>(relaxed = true) {
            io.mockk.every { state } returns kotlinx.coroutines.flow.MutableStateFlow(review)
        }
        shoot("28-memory-cleanup-review") {
            MemoryCleanupScreen(viewModel = MemoryCleanupViewModel(runner), onBack = {})
        }
    }

    /** 공통 목록 부품(0.38.3 M0) — 묶음·행·섹션 제목·빈 화면·되돌리기 스낵바. 화면 적용 전 부품만. */
    @Test fun listParts() {
        shoot("23-list-parts") {
          // 스낵바는 실제처럼 화면 아래에 붙인다(부품 모음이라도 위치를 오해하지 않게).
          androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(vertical = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader("메모 3", androidx.compose.ui.Modifier.padding(horizontal = 16.dp), actionLabel = "모두 보기", onAction = {})
                KosmosListGroup(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    val rows = listOf(Triple("자전거 비밀번호는 134", "10월 12일", listOf("자전거")), Triple("회사 주차는 B2 구역 117번", "오늘", listOf("주차")), Triple("아침엔 커피 대신 녹차", "어제", listOf("자동")))
                    rows.forEachIndexed { i, (t, d, tags) ->
                        KosmosListRow(t, supporting = d, tags = tags, divider = i < rows.lastIndex) {
                            DeleteIconButton(onClick = {})
                        }
                    }
                }
                SectionHeader("이동", androidx.compose.ui.Modifier.padding(horizontal = 16.dp))
                KosmosListGroup(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
                    KosmosListRow("메모·할 일", leadingIcon = R.drawable.ic_ph_note, onClick = {}, divider = true)
                    KosmosListRow("문서", leadingIcon = R.drawable.ic_ph_file_text, onClick = {})
                }
                EmptyState(
                    R.drawable.ic_ph_note, "아직 저장한 메모가 없어요.\n채팅에서 \"기억해 줘\"라고 말하면 여기 쌓여요.",
                    actionLabel = "채팅으로 가기", onAction = {}
                )
            }
            // [WHY] 앱의 showUndo 와 같은 경로(SnackbarData → 기본 액션 색 inversePrimary)로 그린다 — 손으로 만든 TextButton 은 primary 색이라
            // 다크 "되돌리기" 대비 결함을 장면이 보여 주지 못했다(0.38.3 M6).
            androidx.compose.material3.Snackbar(
                snackbarData = object : androidx.compose.material3.SnackbarData {
                    override val visuals = object : androidx.compose.material3.SnackbarVisuals {
                        override val message = "메모를 지웠어요."
                        override val actionLabel: String? = "되돌리기"
                        override val withDismissAction = false
                        override val duration = androidx.compose.material3.SnackbarDuration.Short
                    }
                    override fun performAction() = Unit
                    override fun dismiss() = Unit
                },
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp)
            )
          }
        }
    }

    /** 모델 관리(0.38.3 M0 — 카탈로그에 없던 화면). */
    @Test fun modelManagement() {
        // [WHY] 실제 내려받기 예약기는 WorkManager 를 요구한다(테스트 앱에는 없음) — 대기 상태만 내는 가짜로.
        val useCase = io.mockk.mockk<DownloadModelUseCase>(relaxed = true) {
            io.mockk.every { status } returns kotlinx.coroutines.flow.flowOf(ModelDownloadStatus.Idle)
        }
        val vm = ModelManagementViewModel(useCase)
        shoot("24-model-management") { ModelManagementScreen(viewModel = vm, onBack = {}) }
    }

    /** 모델 관리의 뜻 검색 모델(선택) 항목 — 받기 전·설치됨 두 상태(0.41.2). */
    @Test fun modelManagementEmbedding() {
        shoot("39-model-embedding") {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.kosmos.app.feature.settings.EmbeddingModelSection(com.kosmos.app.feature.settings.EmbeddingModelUi.NotInstalled, {}, {}, {}, {})
                // 기억 뜻 검색 준비 줄(0.41.3) — 준비 안 된 기억 42개
                com.kosmos.app.feature.settings.EmbeddingModelSection(
                    com.kosmos.app.feature.settings.EmbeddingModelUi.Installed, {}, {}, {}, {},
                    memory = com.kosmos.app.feature.settings.MemorySemanticUi(pending = 42, total = 42, work = com.kosmos.app.platform.memory.MemoryEmbedder.State.Idle)
                )
            }
        }
    }

    /** 문서 뷰어 — 스프레드시트(0.38.3 M0 — 카탈로그에 없던 화면). */
    @Test fun documentViewer() {
        val sheet = Sheet(
            name = "1월",
            rows = listOf(SheetRow(0, listOf(
                SheetCell(0, "날짜"), SheetCell(1, "항목"), SheetCell(2, "금액")
            ))) + (1..30).map { r ->
                SheetRow(r, listOf(
                    SheetCell(0, "10-$r"), SheetCell(1, "항목$r"), SheetCell(2, "${r * 1000}")
                ))
            },
            columnCount = 3,
            frozenRows = 1
        )
        val state = DocumentViewerState.Spreadsheet("가계부.xlsx", listOf("1월", "2월", "3월"), selected = 0, sheet = sheet)
        shoot("25-document-viewer") { DocumentViewerScreen(state = state, onSelectSheet = {}, onClose = {}) }
    }

    /** 원본 셀 서식이 있는 표(0.38.7 M1) — 칠한 머리 줄(빈 칸 포함)·굵은 합계·빨간 음수·노란 강조, 다크는 어둡게 바꾼 배경. */
    @Test fun documentViewerStyled() {
        val styles = listOf(
            CellStyle(fill = 0xFF8EA9DB.toInt(), bold = true, align = CellStyle.Align.CENTER),
            CellStyle(fontColor = 0xFFC00000.toInt()),
            CellStyle(fill = 0xFFFFFF00.toInt()),
            CellStyle(fill = 0xFFE2EFDA.toInt(), bold = true)
        )
        fun cell(c: Int, text: String, style: Int = SheetCell.NO_STYLE) = SheetCell(c, text, style)
        val sheet = Sheet(
            name = "10월",
            rows = listOf(
                SheetRow(0, listOf(cell(0, "날짜", 0), cell(1, "항목", 0), cell(2, "금액", 0), cell(3, "", 0))),
                SheetRow(1, listOf(cell(0, "10-01"), cell(1, "월급"), cell(2, "3,200,000"))),
                SheetRow(2, listOf(cell(0, "10-03"), cell(1, "월세"), cell(2, "-650,000", 1))),
                SheetRow(3, listOf(cell(0, "10-05"), cell(1, "장보기", 2), cell(2, "-84,300", 1))),
                SheetRow(4, listOf(cell(0, "10-12"), cell(1, "통신비"), cell(2, "-55,000", 1))),
                SheetRow(5, listOf(cell(0, "합계", 3), cell(1, "", 3), cell(2, "2,410,700", 3)))
            ),
            columnCount = 3,
            frozenRows = 1,
            styles = styles
        )
        val state = DocumentViewerState.Spreadsheet("가계부.xlsx", listOf("10월"), selected = 0, sheet = sheet)
        shoot("36-document-styled") { DocumentViewerScreen(state = state, onSelectSheet = {}, onClose = {}) }
    }

    /** 큰 글자(시스템 1.3배)에서 일정 승인 — 큰 시각이 제목 아래로(0.38.3 M0, 0.38.2 이월). */
    @Test fun approvalLargeFont() {
        val request = ApprovalRequest(
            sessionId = SESSION, title = "일정 추가 승인", description = "",
            preview = ApprovalPreview(
                ApprovalPreview.Kind.SCHEDULE, "분기 실적 정리 회의", "2026-10-19T10:00:00", "2026-10-19T11:30:00", "3층 대회의실"
            )
        )
        shoot("26-approval-large-font") {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 1.3f)
            ) { ApprovalSheet(request = request, onApprove = {}, onReject = {}) }
        }
    }

    private fun splashViewModel() = SplashViewModel(
        object : ModelRunner by modelRunner {
            override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.InitializingEngine)
        }
    )

    // 장면은 다 모이고 별까지 나온 시각에 멈춰 찍는다.
    @Test fun splash() {
        val vm = splashViewModel()
        shoot("14-splash") { SplashScreen(viewModel = vm, onInitializationComplete = {}, previewTimeMs = 3000L) }
    }

    @Test fun splashGalaxy() {
        val vm = splashViewModel()
        shoot("34-splash-galaxy") {
            SplashScreen(
                viewModel = vm, onInitializationComplete = {},
                splashStyle = SplashStyle.GALAXY, previewTimeMs = 3000L
            )
        }
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
