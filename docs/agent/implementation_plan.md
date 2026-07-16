# 프로필 메모리(Profile Memory) 연동 및 프롬프트 주입 계획

현재 `SettingsDataStore` 및 `SettingsScreen`에 응답 스타일(Response Style)을 저장하는 로직은 이미 구현되어 있으나, 정작 **Gemma 모델의 시스템 프롬프트(System Instruction)에는 반영되지 않고 하드코딩되어 있는 문제**를 해결합니다.

## 📝 User Review Required
> [!IMPORTANT]
> 1. **가벼운 토큰 사용량**: 우려하셨던 것처럼 전체 히스토리나 불필요한 데이터를 모두 주입하지 않고, 오직 "User's preferred response style: [선호 스타일]" 이라는 **20자 내외의 아주 짧은 지시문**만 동적으로 주입합니다.
> 2. **Clean Architecture 호환**: `ContextBuilder`가 `SettingsDataStore`(또는 `ProfileRepository`)를 구독(first)하여 컨텍스트 조립 단계에서만 상태를 읽어오도록 구성합니다.

## Proposed Changes

### [Layer: Context Management]
#### [MODIFY] [ContextBuilder.kt](file:///C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/context/ContextBuilder.kt)
- `@Inject`에 `SettingsDataStore` (또는 해당 책임을 지는 Repository)를 추가합니다.
- `Context` 데이터 클래스에 `val responseStyle: String` 필드를 추가합니다.
- `build(sessionId: String)` 내부에서 `settingsDataStore.responseStyleFlow.first()`를 호출하여 현재 저장된 스타일 값을 읽어오고 `Context` 객체에 담아 반환합니다.

#### [MODIFY] [PromptAssembler.kt](file:///C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt)
- `buildSystemBlock(responseStyle: String)`으로 시그니처를 변경합니다.
- 기존의 하드코딩된 시스템 프롬프트 하단에 다음 로직을 추가합니다:
  ```kotlin
  if (responseStyle != "DEFAULT") {
      appendLine("User's preferred response style: $responseStyle. You MUST strictly follow this style when answering.")
  }
  ```

## Verification Plan
### Manual Verification
1. 앱의 설정(Settings) 화면에 진입하여 응답 스타일을 "친절하게 이모지 많이 써줘" 또는 "Very concise and professional" 등으로 변경합니다.
2. 채팅 화면으로 돌아와 아무 질문("오늘 날씨 어때?")이나 입력합니다.
3. 봇의 응답이 설정 화면에서 지정한 페르소나/스타일에 완벽하게 동기화되어 출력되는지 확인합니다.
4. Logcat을 통해 최종 조립된 `ChatPrompt`의 `systemInstruction` 부분에 해당 지시문이 정상적으로 1~2줄 추가되었는지 눈으로 점검합니다.
