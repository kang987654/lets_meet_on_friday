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
    override fun getCurrentTemperatureCelsius(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return tempTenths / 10.0f
    }
}

