# Changelog

## [2026-07-14] 인앱 캘린더 단일 시간제 전환 및 Audit Logs 화면 구현, QA 버그 수정

### 주요 변경 사항
1. **채팅 UI & 컨텍스트 엔진 개선**:
   - `ChatScreen.kt`에서 `TopAppBar`를 제거하여 모바일 화면 효율을 극대화했습니다.
   - `ResponseParser.kt`에 Regex 기반 JSON Sanitizer 및 파싱 Fallback 장치를 탑재하여 Gemma 모델의 오염된 JSON(쉼표 중복 등) 출력을 복원할 수 있도록 처리했습니다.
   - `ContextBuilder.kt` 슬라이딩 윈도우의 최근 대화 개수를 150개, 토큰 수 상한을 3,000에서 **8,000 토큰**으로 올려 S25 Ultra의 가용 메모리 프로필을 온전히 활용하도록 최적화했습니다.
   - `PromptAssembler.kt`에 시스템 시계(`System.currentTimeMillis()`)를 동적 바인딩해 주어 LLM의 날짜 및 시간 환각을 예방했습니다.

2. **인앱 캘린더 일정 관리 및 시간 단일화**:
   - `TaskEntity.kt` 및 `TaskItem.kt`에 `dueDateIso` 컬럼을 정식 추가하고 Room DB 버전을 2로 상향하였습니다.
   - 사용자 피드백에 따라 종료 시간(`endIso`)을 제거하고 시작 시간(`startIso`)만 단일 "일정 시간"으로 로컬 DB에 기록하도록 `CalendarAgent.kt`를 변경했습니다.
   - `GetTodayScheduleUseCase.kt`와 `CalendarScreen.kt`가 디바이스 캘린더 대신 로컬 DB(`TaskRepository`)를 조회하게 함으로써 시스템 권한 의존성을 없애고 완전히 인앱 달력으로 작동하도록 전면 개편했습니다.

3. **감사 로그(Audit Logs) 리스트 화면 신규 구현**:
   - `feature/settings/` 패키지 하위에 `AuditScreen.kt` 및 `AuditViewModel.kt`를 생성하고 Paging 3 라이브러리를 통해 감사 내역을 페이징 처리해 출력하는 화면을 구축했습니다.
   - `AppDestination.kt` 및 `AppNavHost.kt`에 라우팅 정보를 추가하고 설정 화면의 "View Audit Logs" 버튼과 원활하게 네비게이션 연결을 마쳤습니다.

4. **Gemma 모델 오디오 입력(음성 인식) 최적화 및 버그 픽스**:
   - `MediaRecorder`의 AAC 인코딩 방식으로 인해 발생하던 `miniaudio decoder error code:-10` 크래시를 해결했습니다.
   - `AudioRecorder.kt`를 `AudioRecord` API로 전면 개편하여, 백그라운드 코루틴을 통해 Raw PCM 데이터를 추출하고 16kHz, Mono, 16-bit PCM 포맷의 44바이트 헤더를 갖춘 표준 `.wav` 파일을 생성하도록 수정했습니다.
   - `litertlm-gemma4` 스킬 문서에 해당 오디오 제약 사항(16kHz, Mono, float32 소비 특성)을 문서화하여 추후 유사 장애를 방지했습니다.
