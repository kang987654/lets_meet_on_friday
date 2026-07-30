---
name: android-friday-skill
description: Rules and guidelines for Android Development (Compose, Robolectric, Hilt, AGP) in the lets_meet_on_friday project.
---

# Android Development Guidelines

## E2E & Integration Testing (Robolectric + Compose)
- **Manual ViewModel Injection**: To avoid Robolectric `hiltViewModel()` crashes, you MUST manually instantiate ViewModels in the test class and inject them into the Compose Screen (`ChatScreen(viewModel = vm)`).
- **Hilt Dependency Preservation**: When using `@UninstallModules`, explicitly re-bind deleted dependencies (like Tokenizer) using `@BindValue` (Mocking).
- **Async Side-Effects**: Background coroutines do not sync with `waitForIdle()`. You MUST explicitly wait using a `while` loop (Polling) with `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()`.
- **Safe Node Search**: Do NOT search TextFields by hint (`onNodeWithText`). ALWAYS use `hasSetTextAction()`, `onNodeWithContentDescription("Attach")` / `"Send"`, or `testTag`.
- **Test Mock Class Modifiers**: Any platform/speech service methods overridden by test mocks (e.g. `AudioRecorder.stopRecording()`) MUST be declared as `open suspend fun` in production code.

## Gradle & AGP 9.0+
- **Kotlin Android Plugin**: The project uses AGP 9.0+. When creating new Android library modules (core, domain, etc.), NEVER specify `id("org.jetbrains.kotlin.android")` in `build.gradle.kts`. It is built-in, and redeclaring it causes a crash.

## UI & Architecture
- **AnimatedContent Layout Integrity**: Place button size and `clip` constraints on the top-level modifier, and use `fillMaxSize()` for internal icons to prevent breaking layouts.
- **KDoc Headers**: Any new core classes (Orchestrator, Agent, UseCase) MUST include KDoc headers (`/** ... */`) detailing: Role, Architecture Context (Layer/Dependencies), and Key Flow.
