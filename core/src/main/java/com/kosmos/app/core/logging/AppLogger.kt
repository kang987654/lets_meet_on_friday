package com.kosmos.app.core.logging

import java.lang.reflect.Method

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
