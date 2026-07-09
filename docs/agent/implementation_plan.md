# Local Friday 대규모 리팩토링 및 재작성 계획 (Refactoring Plan)

## 1. 개요 (Background)
현재 앱의 가장 기본적인 챗봇 기능에 에러가 존재하며, 캘린더 등 부가 기능은 UI 목업 수준에 머물러 있습니다. 이를 해결하기 위해 단순 코드 수정이 아닌, **데이터 파이프라인(입력-의도분석-프롬프트생성-추론-파싱) 관점에서의 전면적인 검토 및 재작성**이 필요합니다. 

앞서 논의된 3가지 핵심 합의 사항을 바탕으로 진행합니다:
1. **Git 관리**: 메인 브랜치에서 기능 단위 마이크로 커밋(Micro-commit) 진행 (`feat:`, `fix:` 등의 Conventional Commits 적용)
2. **문서 관리**: 기존 `documents/v1` 원본은 보존하고, `docs/` 폴더를 새로 만들어 진화형(Docs-as-code) 문서를 구축
3. **테스트 전략**: 구문 유닛 테스트보다 통합(Integration) 및 E2E 테스트(데이터 파이프라인 검증) 집중

---

## 2. 사용자 검토 필요 (User Review Required)

> [!IMPORTANT]
> 피드백을 반영하여 계획을 업데이트했습니다. **아래 '검토 완료 및 진행 승인'을 해주시면, 바로 `docs/` 세팅부터 작업을 시작하고 진행 상황을 `task.md`를 통해 추적하겠습니다.**

*(참고: Android 테스트 프레임워크는 앱 구동 없이 빠르게 파이프라인 로직만 테스트하기 좋은 `Robolectric`과, 실제 UI 화면을 클릭해보며 테스트하는 `Compose Test Rule`이라는 Android 공식 표준 조합으로 제가 알아서 최적화하여 세팅하겠습니다.)*

---

## 3. 제안하는 변경 사항 (Proposed Changes)

작업은 의존성이 낮은 문서 및 테스트 환경 세팅부터, 핵심 파이프라인(챗봇), 부가 파이프라인(캘린더, 음성) 순으로 진행됩니다.

### 단계 1: Docs-as-code 마이그레이션
- [NEW] `docs/` 디렉토리 생성
- [MODIFY] `documents/v1/` 하위의 문서를 원본 훼손 없이 `docs/`로 **복사(Copy)**
- 이후의 기획 및 아키텍처 변경사항은 오직 `docs/` 내의 문서에만 동기화(Update)

### 단계 2: E2E / 통합 테스트 환경 구축
- [NEW] `app/src/test/` 및 `app/src/androidTest/` 환경 셋업
- [MODIFY] `app/build.gradle.kts`에 통합 테스트 관련 의존성 추가 (Hilt Test, Robolectric, Compose Test 등)
- [NEW] 데이터 파이프라인 테스트용 기반 클래스 생성

### 단계 3: Core Chatbot 파이프라인 수정 (The "Brain")
- [MODIFY] `AssistantOrchestrator.kt`: 의도 분류 및 모델 결과 파싱 로직의 연결부 에러 픽스
- [MODIFY] `PromptAssembler.kt`: 프롬프트 조립 시 컨텍스트 누락 문제 해결
- [MODIFY] `GemmaModelRunner.kt`: 실제 모델 추론부 응답 처리 안정화
- [NEW] `ChatPipelineIntegrationTest.kt`: 사용자 텍스트 -> 프롬프트 -> 응답 -> 상태 반영까지의 통합 테스트 작성

### 단계 4: 도구(Tool) 및 에이전트(Agent) 연결 (목업 탈피)
- [MODIFY] `CalendarAgent.kt` & `AndroidCalendarTool.kt`: 목업 로직을 실제 Android Calendar Provider API 호출로 교체
- [MODIFY] `AndroidSpeechToTextTool.kt`: 음성 인식 파이프라인 실제 연동
- [NEW] `ToolIntegrationTest.kt`: 에이전트가 도구를 호출하고 결과를 반환받는지 확인하는 테스트

### 단계 5: DB 연동 및 메모리 영속화
- [MODIFY] `LocalFridayDatabase.kt` 및 DAO: 트랜잭션 충돌이나 비동기 처리 오류 픽스
- [MODIFY] `SendChatMessageUseCase.kt`: 응답 완료 후 Conversation / Audit Memory 정상 저장 보장

---

## 4. 검증 계획 (Verification Plan)

### 자동화 테스트 (Automated Tests)
- 세팅된 통합 테스트(Integration Tests)를 돌려 파이프라인(텍스트 입력 -> 파싱 -> DB 저장)의 성공 여부를 검증합니다.

### 수동 검증 (Manual Verification)
- 각 주요 파이프라인 복구가 끝날 때마다 컴파일이 가능한지 확인하고, 필요 시 사용자님께 에뮬레이터 또는 실기기 빌드 테스트를 요청드리겠습니다.
