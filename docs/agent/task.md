# Phase: In-App Model Downloader

- `[x]` `app/build.gradle.kts`: OkHttp 라이브러리 추가 확인 및 적용
- `[x]` `ModelDownloadService.kt` (Data Layer): OkHttp를 사용한 파일 스트리밍 다운로드 로직 및 진행률 Flow 반환 구현
- `[x]` `DownloadModelUseCase.kt` (Domain Layer): 다운로드 서비스 호출 및 GemmaRuntimeManager 트리거
- `[x]` `GemmaRuntimeManager.kt` (Runtime Layer): `context.filesDir` 스캔을 통해 다운로드된 파일 우선 인식하도록 구조 확장
- `[x]` `ModelManagementViewModel.kt` (UI Layer): 다운로드 상태(대기, 진행률, 완료, 에러) StateFlow 관리
- `[x]` `ModelManagementScreen.kt` (UI Layer): 기본 모델 다운로드 링크 표시, 커스텀 URL 입력 필드, 포그라운드 진행률 다이얼로그 구성
- `[x]` 빌드 및 앱 실행 검증
