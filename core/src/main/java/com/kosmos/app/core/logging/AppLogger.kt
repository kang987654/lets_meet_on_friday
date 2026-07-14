package com.kosmos.app.core.logging

import android.util.Log

object AppLogger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
}
