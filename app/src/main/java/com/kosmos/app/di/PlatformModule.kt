package com.kosmos.app.di

import com.kosmos.app.domain.tool.CalendarTool
import com.kosmos.app.platform.calendar.AndroidCalendarTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import com.kosmos.app.domain.tool.ModelDownloader
import com.kosmos.app.data.network.ModelDownloadService

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    abstract fun bindModelDownloader(
        impl: ModelDownloadService
    ): ModelDownloader
    @Binds
    abstract fun bindCalendarTool(
        impl: AndroidCalendarTool
    ): CalendarTool

    @Binds
    abstract fun bindTemperatureProvider(
        impl: com.kosmos.app.platform.device.BatteryTemperatureProvider
    ): com.kosmos.app.platform.device.TemperatureProvider
}
