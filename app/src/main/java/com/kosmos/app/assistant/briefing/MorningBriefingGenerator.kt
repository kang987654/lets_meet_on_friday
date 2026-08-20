package com.kosmos.app.assistant.briefing

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.assistant.episode.EpisodeBoundaryManager
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.GenerateBriefingUseCase
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MorningBriefingGenerator]
 * 아침 브리핑 본문을 **엔진 Ready 시점에 편승해** 생성하고 타임라인에 비서 메시지로
 * 저장합니다 (expand.md A4·A4+ — 하이브리드 결정: 정시 알림은 미리보기, 본문은 여기).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Briefing)
 * - **Dependencies**: [GenerateBriefingUseCase], [EpisodeBoundaryManager], [AuditTrailService]
 *
 * [WHY] 백그라운드 워커가 모델을 직접 로드하는 설계는 기각됐다 — 앱이 백그라운드로 가면
 * onStop/onTrimMemory 가 즉시 3.6GB 를 해제하는 수명주기(0.16.2)와 정면 충돌하고, 재로드는
 * 발열·배터리 최악이다. [EpisodeSummarizeScheduler] 와 같은 "Ready 편승"이 이 코드베이스의
 * 전례다: 생성 여부의 진실은 DB(오늘자 BRIEFING 메시지 존재)이고, 놓친 아침은 다음 Ready 가
 * 복원한다.
 */
@Singleton
class MorningBriefingGenerator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val episodeRepository: EpisodeRepository,
    private val taskRepository: TaskRepository,
    private val getTodaySchedule: GetTodayScheduleUseCase,
    private val generateBriefing: GenerateBriefingUseCase,
    private val boundaryManager: EpisodeBoundaryManager,
    private val auditTrailService: AuditTrailService,
    private val metricsCollector: RuntimeMetricsCollector,
    private val modelRunner: ModelRunner,
    private val notificationScheduler: com.kosmos.app.work.BriefingNotificationScheduler
) {

    /**
     * 브리핑 저장 완료 신호 — ChatViewModel 이 구독해 라이브 테일을 재동기화한다.
     * [WHY] ConversationDao 에 Flow 쿼리가 없어 외부 저장이 화면에 자동 반영되지 않는다.
     * replay=1 — 신호가 구독(화면 진입)보다 먼저 나도 유실되지 않게 (ShareIntentHandler 전례).
     * 스플래시 중 생성분은 앵커 이전이라 히스토리 Paging 이 자연 표시하므로, 이 신호는
     * 앵커 이후(앱 사용 중 Ready 재전이) 케이스만 담당한다.
     */
    private val _briefingSaved = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val briefingSaved: SharedFlow<Unit> = _briefingSaved

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // [WHY] Ready 요동(재진입 시 Ready→FileFound→Ready, 0.16.2)으로 collect 가 연달아 불려도
    // 생성은 1회여야 한다 — 뮤텍스 직렬화 + DB 판정(⑶)이 이중 방어.
    private val mutex = Mutex()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Ready 편승 구독을 시작합니다 — KosmosApp.onCreate 가 부른다 (멱등).
     *
     * [WHY] init 에서 구독하면 Hilt 테스트가 ChatViewModel 에 주입하는 것만으로 백그라운드
     * 추론이 돌 수 있다 — 테스트 실행 시각(09:17 전/후)에 따라 결과가 갈리는 최악의 플레이크.
     * 명시적 start() 는 HiltTestApplication 에서는 불리지 않아 테스트가 자동으로 격리된다.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // 초기 예약 — KEEP 이라 이미 예약돼 있으면 무해하게 지나간다 (앱 시작마다 멱등).
            if (settingsDataStore.briefingEnabledFlow.first()) {
                notificationScheduler.ensureScheduled(settingsDataStore.briefingTimeMinutesFlow.first())
            }
        }
        scope.launch {
            modelRunner.loadState.collect { state ->
                if (state is ModelLoadState.Ready) {
                    runCatching { maybeGenerate() }
                        .onFailure { AppLogger.e(TAG, "브리핑 생성 실패", it) }
                }
            }
        }
    }

    // [WHY] now 를 인자로 받는다 — 판정이 벽시계에 묶이면 테스트가 실행 시각(09:17 전/후)에
    // 따라 갈리는 플레이크가 된다 (EpisodeBoundaryManager.onUserMessage 의 fake clock 전례).
    internal suspend fun maybeGenerate(now: Long = System.currentTimeMillis()) = mutex.withLock {
        val zone = ZoneId.systemDefault()

        val enabled = settingsDataStore.briefingEnabledFlow.first()
        val briefingMinutes = settingsDataStore.briefingTimeMinutesFlow.first()
        val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val todayCount = (conversationRepository
            .countByInputTypeSince(InputType.BRIEFING, startOfToday) as? AppResult.Success)
            ?.data ?: return@withLock // 판정을 못 읽으면 중복 생성 위험 — 다음 Ready 로 미룬다

        if (!shouldGenerateBriefing(enabled, now, briefingMinutes, todayCount, zone)) return@withLock

        // [WHY] 발열 게이트 — 브리핑은 급하지 않은 추론이다. 경고 온도면 다음 Ready 로 미룬다
        // (EpisodeSummarizeScheduler 와 같은 기준. 같은 Ready 세션 안 재시도는 과설계로 기각).
        if (metricsCollector.getCurrentTemp() >= Constants.THERMAL_WARNING_CELSIUS) {
            AppLogger.w(TAG, "발열로 브리핑 연기")
            return@withLock
        }

        val materials = collectMaterials(zone, now)
        val briefing = when (val result = generateBriefing(materials)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.e(TAG, "브리핑 추론 실패: ${result.error}")
                return@withLock // DB 에 안 남았으므로 다음 Ready 가 자연 재시도
            }
        }

        // [WHY] activeSessionId 를 쓴다 — ChatViewModel 이 이 값으로 라이브 테일을 조회하므로
        // 다른 id 로 저장하면 loadMessages 사정권 밖이 된다. 앱을 한 번도 안 연 기기면 값이
        // 없을 수 있어 여기서 만들어 저장한다(다음 채팅이 그대로 이어받음).
        val sessionId = sessionStore.activeSessionIdFlow.first()
            ?: UUID.randomUUID().toString().also { sessionStore.saveActiveSessionId(it) }

        val episodeId = boundaryManager.onAssistantInitiatedMessage(sessionId, now)
        val saved = conversationRepository.save(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = ChatMessage.Role.ASSISTANT,
                content = briefing,
                inputType = InputType.BRIEFING,
                createdAt = now,
                episodeId = episodeId
            )
        )
        if (saved is AppResult.Failure) {
            AppLogger.e(TAG, "브리핑 저장 실패: ${saved.error}")
            return@withLock
        }

        auditTrailService.logModelRun(
            sessionId = GenerateBriefingUseCase.SESSION_ID,
            prompt = "morning briefing (events=${materials.events.size}, " +
                "tasks=${materials.pendingTasks.size}, episodes=${materials.recentEpisodes.size})",
            output = briefing
        )
        _briefingSaved.tryEmit(Unit)
        AppLogger.d(TAG, "아침 브리핑 생성 완료")
    }

    private suspend fun collectMaterials(zone: ZoneId, now: Long): GenerateBriefingUseCase.BriefingMaterials {
        val schedule = (getTodaySchedule(ScheduleData.RangeType.TODAY) as? AppResult.Success)?.data
        val tasks = (taskRepository.getPendingTasksData(0, MAX_TASKS) as? AppResult.Success)
            ?.data.orEmpty().filterNot { it.isCompleted }
        val episodes = (episodeRepository.getEpisodes(0, MAX_EPISODES) as? AppResult.Success)
            ?.data.orEmpty().filter { it.status == EpisodeStatus.SUMMARIZED }

        val dateLabel = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))

        return GenerateBriefingUseCase.BriefingMaterials(
            dateLabel = dateLabel,
            events = schedule?.events.orEmpty(),
            // [WHY] 일정 조회 자체가 실패해도(schedule == null) 브리핑은 나간다 — 대신 기기
            // 캘린더 미확인으로 취급해 "일정 없음"이 거짓이 되지 않게 한다 (EC4).
            deviceCalendarFailed = schedule?.deviceCalendarFailed ?: true,
            pendingTasks = tasks,
            recentEpisodes = episodes
        )
    }

    companion object {
        private const val TAG = "MorningBriefing"

        // [WHY] 재료 상한 — 절단은 유스케이스가 하지만, 수집 단계에서 상식적 상한을 둬
        // 대형 DB 에서 전량 로드를 피한다.
        private const val MAX_TASKS = 20
        private const val MAX_EPISODES = 3
    }
}

/**
 * 오늘 브리핑을 생성해야 하는지 판정합니다 — 테스트 가능하도록 순수 함수로 분리
 * (WorkManagerModelDownloadScheduler.toDownloadStatus 전례).
 */
internal fun shouldGenerateBriefing(
    enabled: Boolean,
    nowMs: Long,
    briefingMinutes: Int,
    todayCount: Int,
    zone: ZoneId
): Boolean {
    if (!enabled) return false
    if (todayCount > 0) return false
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val triggerMs = today.atStartOfDay(zone).toInstant().toEpochMilli() + briefingMinutes * 60_000L
    return nowMs >= triggerMs
}
