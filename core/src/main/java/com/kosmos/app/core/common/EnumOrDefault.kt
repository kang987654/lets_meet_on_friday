package com.kosmos.app.core.common

/**
 * 저장된 문자열을 enum 으로 되돌리되, 모르는 값이면 [default] 를 돌려줍니다.
 *
 * [WHY] `try { X.valueOf(s) } catch (e: Exception) { default }` 가 저장소 매퍼 다섯 곳에 복제돼
 * 있었다. 예외를 흐름 제어에 쓰지 않고 이름 비교로 찾는다 — 옛 버전이 쓴 값·손상된 행이
 * 크래시 대신 안전한 기본값으로 읽히는 것이 계약이다.
 *
 * [WHY] `inline reified` 가 아니라 [default] 의 선언 클래스로 상수를 찾는다 — `:core` 는 JVM 21,
 * `:data` 는 JVM 17 바이트코드라 core 의 inline 함수를 data 에 인라인할 수 없다(컴파일 오류).
 */
fun <E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    default.declaringJavaClass.enumConstants.firstOrNull { it.name == name } ?: default
