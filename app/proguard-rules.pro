# ProGuard rules for Kosmos App

# [WHY] litertlm AAR 은 consumer proguard 규칙을 싣지 않는다. `tool(ToolSet)` 은 kotlin-reflect 로
# `@Tool` 메서드를 찾아 스키마를 만드는데, R8 이 메서드 이름을 바꾸거나 어노테이션·메타데이터를
# 벗기면 **예외 없이 빈 툴 목록**이 되어 release 빌드에서만 툴 호출이 조용히 죽는다.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.kosmos.app.runtime.gemma.KosmosToolDeclarations$* { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class kotlin.Metadata { *; }
