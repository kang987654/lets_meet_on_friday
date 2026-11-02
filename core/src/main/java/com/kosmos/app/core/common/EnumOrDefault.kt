package com.kosmos.app.core.common

/**
 * 저장된 문자열을 enum 으로 되돌리되, 모르는 값이면 [default] 를 돌려줍니다.
 *
 * [WHY] `try { X.valueOf(s) } catch (e: Exception) { default }` 가 저장소 매퍼 다섯 곳에 복제돼
 * 있었다. 예외를 흐름 제어에 쓰지 않고 [default] 의 선언 클래스 상수에서 이름 비교로 찾는다 —
 * 옛 버전이 쓴 값·손상된 행이 크래시 대신 안전한 기본값으로 읽히는 것이 계약이다.
 */
fun <E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    default.declaringJavaClass.enumConstants.firstOrNull { it.name == name } ?: default
