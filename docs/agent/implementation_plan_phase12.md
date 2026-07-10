# Phase 12: 프로젝트 패키지 및 이름 리팩토링 (LocalFriday ➔ Kosmos)

사용자 요청에 따라 프로젝트 전반에 걸쳐 사용되던 임시 명칭 `localfriday`를 `kosmos`로 변경하고, 스마트폰 설치 시 표시되는 앱 이름(Label)을 정상적으로 출력되도록 수정합니다.

## User Review Required

> [!WARNING]
> **대규모 리팩토링 경고**
> 150여 개의 코드 파일과 디렉터리 구조를 일괄 변경하는 작업입니다. 안전한 이전을 위해 직접 파이썬(Python) 스크립트를 작성하여 텍스트 및 패키지 구조를 변경할 예정입니다. (규칙 2번에 의거하여 불안정한 `git mv` 대신 스크립트로 파일을 안전하게 이동/복사 후 `git add` 처리합니다.)

1. **대소문자 치환 룰 동의**: 
   - `localfriday` ➔ `kosmos` (예: `com.localfriday.app` ➔ `com.kosmos.app`)
   - `LocalFriday` ➔ `Kosmos` (예: `LocalFridayApp.kt` ➔ `KosmosApp.kt`)
   - (문서 파일인 `.md`, `docs/` 등은 이번 치환에서 제외합니다.)

## Proposed Changes

### 1. `app_name` (앱 이름) 수정
#### [MODIFY] `app/src/main/res/values/strings.xml`
- `<string name="app_name">Local Friday</string>` ➔ `<string name="app_name">Kosmos</string>` 로 변경합니다.

#### [MODIFY] `app/src/main/AndroidManifest.xml`
- `<application>` 태그 내에 `android:label="@string/app_name"` 속성을 추가하여, 시뮬레이션 기기에 패키지명 대신 정상적인 앱 이름이 표시되도록 수정합니다.

### 2. 패키지 및 코드 리팩토링 스크립트 실행
- **스크립트 방식**: `scratch/refactor_kosmos.py`를 작성 및 실행하여 아래 작업을 일괄 처리합니다.
  - `build.gradle.kts` 내의 `applicationId` 및 `namespace` 변경
  - `app/src/main/java/com/localfriday/app` ➔ `app/src/main/java/com/kosmos/app` 로 폴더 구조 이전
  - `test` 및 `androidTest` 폴더 내의 구조도 동일하게 이전
  - 모든 `.kt` 파일 내부의 `package` 및 `import` 구문 문자열 치환
  - `LocalFridayApp` 클래스명을 `KosmosApp`으로 치환 및 파일명 변경

## Verification Plan

### Automated Tests
- 리팩토링 스크립트 실행 후 `./gradlew clean build` 및 `./gradlew :app:testDebugUnitTest` 명령어를 통해 패키지 참조 에러가 없는지(빌드 성공 여부) 완벽하게 검증합니다.

### Manual Verification
- `AndroidManifest.xml`과 `strings.xml`이 올바르게 매핑되었는지 확인합니다.
- 패키지 폴더(`com/kosmos/app`)가 중첩(`app/app/...`) 없이 깔끔하게 이동되었는지 확인합니다.
