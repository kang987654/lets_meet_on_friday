# Local Friday 개발 계획 (plan.md)

## 1. 목표
- Android MVP 개발
- 오프라인 우선 개인 AI 비서
- Gemma 4 E4B-it 로컬 실행
- 텍스트 + push-to-talk 음성 + 이미지 입력
- 캘린더 조회 / 일정 초안 / 승인
- 메모리 5계층 저장
- 질문 단위 검색 ON
- export/import 지원

---

## 2. 실제 디렉토리 트리

```text
com.localfriday.app
├─ app/
│  ├─ LocalFridayApp.kt
│  ├─ MainActivity.kt
│  └─ di/
│     ├─ AppModule.kt
│     ├─ ModelModule.kt
│     ├─ MemoryModule.kt
│     ├─ AgentModule.kt
│     └─ PlatformModule.kt
├─ core/
│  ├─ common/
│  │  ├─ Result.kt
│  │  ├─ AppError.kt
│  │  └─ Constants.kt
│  ├─ config/
│  │  ├─ AppConfig.kt
│  │  ├─ FeatureFlags.kt
│  │  └─ ModelConfig.kt
│  ├─ logging/
│  │  ├─ AppLogger.kt
│  │  └─ AuditLogger.kt
│  └─ security/
│     ├─ PermissionPolicy.kt
│     ├─ ApprovalPolicy.kt
│     └─ Redaction.kt
├─ domain/
│  ├─ model/
│  ├─ memory/
│  ├─ modelrunner/
│  ├─ agent/
│  ├─ tool/
│  ├─ policy/
│  └─ usecase/
├─ assistant/
│  ├─ orchestrator/
│  ├─ planner/
│  ├─ session/
│  ├─ persona/
│  └─ harness/
├─ data/
│  └─ local/
│     ├─ db/
│     │  ├─ dao/
│     │  └─ entity/
│     ├─ prefs/
│     ├─ file/
│     └─ repository/
├─ runtime/
│  ├─ gemma/
│  ├─ multimodal/
│  └─ benchmark/
├─ platform/
│  ├─ calendar/
│  ├─ speech/
│  ├─ share/
│  ├─ storage/
│  ├─ permissions/
│  └─ network/
├─ feature/
│  ├─ chat/
│  ├─ voice/
│  ├─ image/
│  ├─ calendar/
│  ├─ approval/
│  ├─ memory/
│  └─ settings/
└─ navigation/
```

---

## 3. 핵심 파일 역할

### app/
- `LocalFridayApp.kt`: 앱 전역 초기화
- `MainActivity.kt`: Compose 진입점
- `di/*.kt`: 의존성 주입 조립

### core/
- `Result.kt`: 공통 성공/실패 래퍼
- `AppError.kt`: 에러 타입 표준화
- `AppLogger.kt`: 앱 로깅
- `AuditLogger.kt`: 감사 로그 기록
- `PermissionPolicy.kt`: 권한 정책 정의

### domain/
- `model/*`: 핵심 도메인 모델
- `memory/*`: 메모리 저장소 인터페이스
- `modelrunner/ModelRunner.kt`: 모델 추상화
- `agent/*`: Agent 인터페이스
- `tool/*`: Tool 인터페이스
- `policy/*`: 승인/검증 인터페이스
- `usecase/*`: 기능 유스케이스

### assistant/
- `AssistantOrchestrator.kt`: 전체 흐름 제어
- `TaskRouter.kt`: 요청 라우팅
- `ContextBuilder.kt`: 문맥 구성
- `PromptAssembler.kt`: 프롬프트 조립
- `ApprovalCoordinator.kt`: 승인 흐름 제어

### data/local/
- `LocalFridayDatabase.kt`: Room DB 엔트리
- `dao/*`: DB 접근
- `entity/*`: DB 엔티티
- `SettingsDataStore.kt`: 앱 설정 저장
- `ModelRegistryStore.kt`: 모델 정보 저장
- `ExportImportManager.kt`: 백업/복원
- `repository/*Impl.kt`: 저장소 구현

### runtime/
- `GemmaModelRunner.kt`: Gemma 실행 연결
- `GemmaRuntimeManager.kt`: 모델 로드/해제 관리
- `ImageInputAdapter.kt`: 이미지 입력 정규화
- `RuntimeMetricsCollector.kt`: 추론 시간/성능 기록

### platform/
- `AndroidCalendarTool.kt`: 캘린더 연결
- `AndroidSpeechToTextTool.kt`: STT 연결
- `ShareIntentHandler.kt`: 공유 인텐트 처리
- `AndroidFileTool.kt`: 파일/URI 접근
- `WebSearchGateway.kt`: 질문 단위 검색 연결

### feature/
- `ChatScreen.kt`: 메인 채팅 UI
- `ChatViewModel.kt`: 채팅 상태/이벤트 처리
- `ApprovalSheet.kt`: 승인 바텀시트
- `CalendarScreen.kt`: 캘린더 화면
- `MemoryManagerScreen.kt`: export/import UI
- `SettingsScreen.kt`: 설정 화면

---

## 4. 세부 Todo / Task Breakdown

### Phase 0. 프로젝트 초기 세팅
- [ ] Android 프로젝트 생성
- [ ] Jetpack Compose 세팅
- [ ] DI(Hilt 또는 Koin) 세팅
- [ ] Navigation 세팅
- [ ] Room 세팅
- [ ] DataStore 세팅
- [ ] 기본 패키지 구조 생성

### Phase 1. 코어/도메인 뼈대
- [ ] `Result.kt`, `AppError.kt`, `Constants.kt` 작성
- [ ] `ChatMessage`, `AssistantResponse`, `UserProfile` 모델 작성
- [ ] `ModelRunner` 인터페이스 작성
- [ ] Memory Repository 인터페이스 작성
- [ ] UseCase 인터페이스/기본 구조 작성
- [ ] `PermissionPolicy`, `ApprovalPolicy` 정의

### Phase 2. 로컬 모델 연결
- [ ] `GemmaModelRunner.kt` 생성
- [ ] `GemmaRuntimeManager.kt` 생성
- [ ] 모델 로드/언로드 흐름 작성
- [ ] 텍스트 생성 호출 연결
- [ ] ModelRegistryStore 구현
- [ ] 설정에서 현재 모델 정보 표시

### Phase 3. 채팅 MVP
- [ ] `ChatScreen.kt` 작성
- [ ] `ChatViewModel.kt` 작성
- [ ] `ChatUiState.kt` 작성
- [ ] `SendChatMessageUseCase.kt` 작성
- [ ] `AssistantOrchestrator.kt` 기본 구현
- [ ] `ContextBuilder.kt` 기본 구현
- [ ] Conversation 저장 연결
- [ ] Profile 저장 연결

### Phase 4. 메모리 스키마 구현
- [ ] Room Entity 작성
  - [ ] ProfileEntity
  - [ ] ConversationEntity
  - [ ] TaskEntity
  - [ ] KnowledgeEntity
  - [ ] AuditEntity
- [ ] DAO 작성
- [ ] Repository 구현체 작성
- [ ] app_settings / model_registry / persona_profile 구조 반영
- [ ] export_manifest 포맷 정의

### Phase 5. 음성 입력
- [ ] STT Tool 인터페이스 정의
- [ ] `AndroidSpeechToTextTool.kt` 구현
- [ ] push-to-talk UI 구현
- [ ] STT 결과를 채팅 입력과 연결
- [ ] 실패 fallback 처리

### Phase 6. 이미지 입력
- [ ] 이미지 첨부 UI 구현
- [ ] `ImageInputAdapter.kt` 구현
- [ ] 스크린샷/문서 이미지 입력 흐름 연결
- [ ] 멀티모달 모델 호출 연결
- [ ] 문서/이미지 요약 응답 포맷 정의

### Phase 7. 비서 오케스트레이션 강화
- [ ] `TaskRouter.kt` 구현
- [ ] `IntentClassifier.kt` 구현
- [ ] `PromptAssembler.kt` 구현
- [ ] `ResponseFormatter.kt` 구현
- [ ] `PreExecutionGuard.kt` 구현
- [ ] `PostExecutionValidator.kt` 구현
- [ ] `AuditTrailService.kt` 구현

### Phase 8. 캘린더 기능
- [ ] CalendarTool 인터페이스 정의
- [ ] `AndroidCalendarTool.kt` 구현
- [ ] 캘린더 읽기 기능 구현
- [ ] 일정 생성 초안 생성 기능 구현
- [ ] `CreateCalendarDraftUseCase.kt` 구현
- [ ] `GetTodayScheduleUseCase.kt` 구현

### Phase 9. 승인 플로우
- [ ] `ApprovalSheet.kt` 작성
- [ ] `ApprovalViewModel.kt` 작성
- [ ] `ApprovalCoordinator.kt` 구현
- [ ] 승인/거부 결과 저장
- [ ] 승인 후 캘린더 저장 연결

### Phase 10. Task/Knowledge/Audit 메모리 확장
- [ ] Task memory 저장 연결
- [ ] Knowledge note 저장 연결
- [ ] Audit event 상세 저장 연결
- [ ] 메모리 조회/정리 화면 구성

### Phase 11. export/import
- [ ] `ExportImportManager.kt` 구현
- [ ] JSON + SQLite 묶음 export 설계
- [ ] import 검증 로직 구현
- [ ] MemoryManagerScreen UI 구현

### Phase 12. 질문 단위 검색
- [ ] 검색 토글 UI 추가
- [ ] `SearchRequest` 모델 구현
- [ ] `SearchAgent.kt` 구현
- [ ] `WebSearchGateway.kt` 구현
- [ ] 질문 단위 ON 정책 가드 구현
- [ ] 검색 사용 Audit 기록

### Phase 13. 공유 인텐트
- [ ] `ShareIntentHandler.kt` 구현
- [ ] 텍스트 공유 처리
- [ ] 이미지 공유 처리
- [ ] 채팅/요약 흐름 연결

### Phase 14. 관찰 가능성/운영 품질
- [ ] `RuntimeMetricsCollector.kt` 구현
- [ ] 성능 로그 기록
- [ ] 발열/오류 추적 포인트 추가
- [ ] 설정 화면에서 로그 확인 기능 추가

---

## 5. 구현 순서 요약
1. 프로젝트 세팅
2. 모델 연결
3. 텍스트 채팅
4. 메모리 저장
5. 음성 입력
6. 이미지 입력
7. 오케스트레이터 강화
8. 캘린더
9. 승인 플로우
10. Task/Knowledge/Audit
11. export/import
12. 질문 단위 검색
13. 공유 인텐트
14. 운영 로그

---

## 6. 완료 기준
- [ ] Gemma 4 E4B-it 로컬 텍스트 채팅 가능
- [ ] 음성 입력 가능
- [ ] 이미지 입력 가능
- [ ] 캘린더 조회 가능
- [ ] 일정 초안 + 승인 후 저장 가능
- [ ] 메모리 5계층 저장 가능
- [ ] 질문 단위 검색 ON 가능
- [ ] export/import 가능
