# 📥 인앱 모델 다운로더 기능 구현 완료

기존에 수동으로 모델을 전송해야 했던 방식에서 벗어나, 앱 내부 UI에서 URL 링크를 통해 즉각적으로 `Gemma 4 E4B` 모델 등을 다운로드하고 로드할 수 있도록 아키텍처를 변경했습니다.

## 📝 구현 내역

### 1. `OkHttp` 기반 다운로드 스트리밍 (Data Layer)
- **`ModelDownloadService.kt`** 추가.
- 안드로이드 내부 전용 저장소인 `context.filesDir/models/` 경로에 모델 파일을 저장하여, 외부 앱 접근을 차단하고 보안을 높였습니다.
- 대용량 파일(3~4GB 이상) 다운로드 시 메모리 오버플로우를 막기 위해 스트림 버퍼링(`okio.sink`) 방식을 채택하였으며, 다운로드 진행률을 0%~100%까지 `Flow<Int>` 로 지속적으로 반환합니다.

### 2. `GemmaRuntimeManager` 동적 스캔 (Runtime Layer)
- 기존에는 `gemma-4-e4b-it-int4.litertlm` 이라는 하드코딩된 이름만 찾았습니다.
- 이제는 `context.filesDir/models` 내부를 동적으로 스캔하여, `*.litertlm` 확장자를 가진 파일 중 **가장 최근에 다운로드(수정)된 파일**을 자동으로 타겟 모델로 로드하도록 진화했습니다.

### 3. Model Management UI 구성 (UI Layer)
- 설정(`SettingsScreen`)에서 **"Manage Models"** 버튼을 눌러 접근할 수 있는 `ModelManagementScreen`을 생성했습니다.
- **기본 제공 모델** (Hugging Face의 Gemma 4 LiteRT-LM 링크) 다운로드 버튼과, 사용자가 **직접 URL을 입력**할 수 있는 커스텀 URL 텍스트 필드를 마련했습니다.
- 백그라운드 전환 시 상태 관리가 복잡해지는 문제를 회피하기 위해, 다운로드 중에는 **포그라운드 다이얼로그(Uncancellable Foreground Dialog)** 를 통해 사용자가 화면 이탈 없이 온전히 다운로드를 마칠 수 있도록 UX를 구성했습니다.

## ✅ 검증 결과
- 의존성(`build.gradle.kts`) 문제 및 Compile Kotlin 에러 수정 완료.
- `AppError` 공통 예외 체계에 알맞은 `NetworkUnavailable` 매핑으로 UI 에러 다이얼로그 노출 완비.
- `gradlew assembleDebug` 빌드 정상 통과.

---

> [!TIP]
> 이제 안드로이드 기기에 앱을 설치한 뒤, `Settings -> Manage Models` 로 이동하여 버튼 하나만 누르면 모델 파일이 앱 내부에 자동으로 다운로드되고 챗 엔진에 즉시 연결됩니다!
