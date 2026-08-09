package com.kosmos.app.di

import com.kosmos.app.domain.tool.CalendarTool
import com.kosmos.app.platform.calendar.AndroidCalendarTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import com.kosmos.app.domain.tool.ModelDownloader
import com.kosmos.app.domain.tool.ModelDownloadScheduler
import com.kosmos.app.data.network.ModelDownloadService
import com.kosmos.app.platform.notification.AndroidDownloadNotifier
import com.kosmos.app.platform.notification.DownloadNotifier
import com.kosmos.app.work.WorkManagerModelDownloadScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    abstract fun bindModelDownloader(
        impl: ModelDownloadService
    ): ModelDownloader

    // [WHY] WorkManager 는 Android 의존이므로 인터페이스는 :domain, 구현은 :app 에 둔다. (ADR-006)
    @Binds
    abstract fun bindModelDownloadScheduler(
        impl: WorkManagerModelDownloadScheduler
    ): ModelDownloadScheduler

    @Binds
    abstract fun bindDownloadNotifier(
        impl: AndroidDownloadNotifier
    ): DownloadNotifier
    @Binds
    abstract fun bindCalendarTool(
        impl: AndroidCalendarTool
    ): CalendarTool

    @Binds
    abstract fun bindTemperatureProvider(
        impl: com.kosmos.app.platform.device.BatteryTemperatureProvider
    ): com.kosmos.app.platform.device.TemperatureProvider

    @Binds
    abstract fun bindDeviceResourceProvider(
        impl: com.kosmos.app.platform.device.AndroidDeviceResourceProvider
    ): com.kosmos.app.platform.device.DeviceResourceProvider
}
