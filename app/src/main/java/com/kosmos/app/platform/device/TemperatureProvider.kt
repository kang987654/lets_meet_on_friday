package com.kosmos.app.platform.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface TemperatureProvider {
    fun getCurrentTemperatureCelsius(): Float
}

class BatteryTemperatureProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : TemperatureProvider {

    @Volatile
    private var cachedTemp: Float = 0f

    @Volatile
    private var cachedAtMs: Long = 0L

    // [WHY] sticky broadcast 조회는 Binder 왕복이라 스트리밍 중 토큰마다 호출하면
    // 기기가 가장 뜨거운 순간에 오버헤드를 얹는다. 배터리 온도는 초 단위로만 변하므로 5초 캐시한다.
    override fun getCurrentTemperatureCelsius(): Float {
        val now = System.currentTimeMillis()
        if (now - cachedAtMs < CACHE_TTL_MS) {
            return cachedTemp
        }
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        cachedTemp = tempTenths / 10.0f
        cachedAtMs = now
        return cachedTemp
    }

    private companion object {
        const val CACHE_TTL_MS = 5_000L
    }
}

