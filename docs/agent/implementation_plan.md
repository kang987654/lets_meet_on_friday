# [인앱 링크 기반 모델 다운로더 구현 계획]

기존에 개발자가 수동으로 USB를 연결하거나 ADB를 통해 모델 파일(`.litertlm`)을 안드로이드 기기에 푸시(Push)하던 아키텍처에서, 앱 내부 UI를 통해 사용자가 직접 URL을 입력(또는 기본 제공된 링크)하여 다운로드할 수 있는 구조로 변경합니다.

## User Review Required
> [!IMPORTANT]
> - 파일 크기(3~4GB 이상)로 인해 다운로드 중 네트워크 끊김, 화면 회전(Configuration Change) 시 대처 방안이 중요합니다. 이번에는 1차적으로 **포그라운드 다이얼로그(취소 불가)** 형태로 화면을 유지하는 방식으로 진행하려고 합니다. 괜찮으신가요?
> - `OkHttp` 네트워크 라이브러리를 통해 스트리밍 방식으로 앱의 내부 저장소(`context.filesDir`)에 저장하게 됩니다.

## Proposed Changes

### 1. Data Layer (`com.localfriday.app.data`)

#### [NEW] `data/network/ModelDownloadService.kt`
- `OkHttp`를 사용하여 모델 파일 스트리밍 다운로드를 수행하고, 진행률(`Flow<Int>`)을 반환하는 코루틴 레포지토리 로직.
- 파일은 보안 및 영속성을 위해 `context.filesDir` (앱 내부 저장소) 경로에 저장됩니다.

#### [NEW] `domain/usecase/DownloadModelUseCase.kt`
- `ModelDownloadService`를 호출하여 다운로드를 실행하고, 성공 시 `GemmaRuntimeManager`에 "다운로드 완료 및 새 모델 파일 적용"을 트리거합니다.

### 2. Runtime Layer (`com.localfriday.app.runtime.gemma`)

#### [MODIFY] `GemmaRuntimeManager.kt`
- 기존의 하드코딩된 파일명 탐색 대신, 내부 저장소(`context.filesDir`)에 존재하는 최신 모델 파일을 동적으로 스캔하여 로드할 수 있도록 로직 확장.

### 3. UI Layer (`com.localfriday.app.ui.feature.settings`)

#### [NEW] `ModelManagementScreen.kt`
- **리스트 UI**: 기본적으로 제공되는 모델 링크(예: Gemma 4 E2B) 리스트업.
- **커스텀 URL UI**: 사용자가 직접 URL 텍스트를 입력하여 다운로드할 수 있는 텍스트 필드와 다운로드 버튼 추가.
- **진행률 UI**: 0%~100% 진행도를 시각적으로 보여주는 프로그레스 다이얼로그 노출.

#### [NEW] `ModelManagementViewModel.kt`
- 다운로드 상태(대기, 진행 중, 완료, 에러)를 `StateFlow`로 관리.

### 4. Dependency (`build.gradle.kts`)
#### [MODIFY] `app/build.gradle.kts`
- 대용량 파일 다운로드를 위해 `OkHttp` 라이브러리(아직 없는 경우) 추가.

---

## Verification Plan

### Manual Verification
1. 모델이 없는 상태에서 앱을 빌드 후 실행합니다.
2. 새로 만들어진 `ModelManagementScreen`에 진입합니다.
3. 텍스트 입력 칸에 임의의 더미 URL이나 실제 다운로드 링크를 넣고 다운로드를 누릅니다.
4. 프로그레스 바가 올라가며 정상적으로 앱 내부 저장소에 파일이 생성되고, 즉시 로드되는지 확인합니다.
