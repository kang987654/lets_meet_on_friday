package com.kosmos.app.core.logging

import java.lang.reflect.Method

/**
 * [AppLogger]
 * 안드로이드 런타임 환경(`android.util.Log`)과 일반 JVM/Robolectric 환경(Standard Output)을 리플렉션으로 감지하여 안전하게 로깅을 제공하는 유틸리티 싱글톤입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Logging)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. `android.util.Log` 클래스를 리플렉션으로 검색합니다.
 * 2. 안드로이드 환경이면 `android.util.Log` 메소드를 실행하고, JVM/단위 테스트 환경이면 Standard Console에 출력합니다.
 */
object AppLogger {
    private var dMethod: Method? = null
    private var eMethod: Method? = null
    private var eThrowableMethod: Method? = null
    private var wMethod: Method? = null

    init {
        try {
            val logClass = Class.forName("android.util.Log")
            dMethod = logClass.getMethod("d", String::class.java, String::class.java)
            eMethod = logClass.getMethod("e", String::class.java, String::class.java)
            eThrowableMethod = logClass.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
            wMethod = logClass.getMethod("w", String::class.java, String::class.java)
        } catch (ignored: Exception) {}
    }

    fun d(tag: String, message: String) {
        val m = dMethod
        if (m != null) {
            try {
                m.invoke(null, tag, message)
                return
            } catch (ignored: Exception) {}
        }
        println("[$tag] D: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            val m = eThrowableMethod
            if (m != null) {
                try {
                    m.invoke(null, tag, message, throwable)
                    return
                } catch (ignored: Exception) {}
            }
        } else {
            val m = eMethod
            if (m != null) {
                try {
                    m.invoke(null, tag, message)
                    return
                } catch (ignored: Exception) {}
            }
        }
        println("[$tag] E: $message")
        throwable?.printStackTrace()
    }

    fun w(tag: String, message: String) {
        val m = wMethod
        if (m != null) {
            try {
                m.invoke(null, tag, message)
                return
            } catch (ignored: Exception) {}
        }
        println("[$tag] W: $message")
    }
}
