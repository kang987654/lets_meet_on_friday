package com.kosmos.app.di

import com.kosmos.app.domain.tool.CalendarTool
import com.kosmos.app.platform.calendar.AndroidCalendarTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import com.kosmos.app.domain.tool.ModelDownloadScheduler
import com.kosmos.app.platform.notification.AndroidDownloadNotifier
import com.kosmos.app.platform.notification.DownloadNotifier
import com.kosmos.app.work.WorkManagerModelDownloadScheduler
import com.kosmos.app.core.common.CurrentLanguage
import com.kosmos.app.core.config.AppLanguageHolder
import com.kosmos.app.platform.device.AndroidDeviceResourceProvider
import com.kosmos.app.platform.device.BatteryTemperatureProvider
import com.kosmos.app.platform.device.DeviceResourceProvider
import com.kosmos.app.platform.device.TemperatureProvider
import com.kosmos.app.platform.document.AndroidDocumentAccess
import com.kosmos.app.platform.document.AndroidDocumentOpener
import com.kosmos.app.platform.document.DocumentAccess
import com.kosmos.app.platform.document.DocumentOpener
import com.kosmos.app.platform.notification.AndroidBriefingNotifier
import com.kosmos.app.platform.notification.AndroidReminderNotifier
import com.kosmos.app.platform.notification.BriefingNotifier
import com.kosmos.app.platform.notification.ReminderNotifier
import com.kosmos.app.widget.GlanceWidgetRefresher
import com.kosmos.app.widget.WidgetRefresher

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    // [WHY] WorkManager 는 Android 의존이므로 인터페이스는 :domain, 구현은 :app 에 둔다. (ADR-006)
    @Binds
    abstract fun bindModelDownloadScheduler(
        impl: WorkManagerModelDownloadScheduler
    ): ModelDownloadScheduler

    @Binds
    abstract fun bindDocumentOpener(
        impl: AndroidDocumentOpener
    ): DocumentOpener

    @Binds
    abstract fun bindDocumentAccess(
        impl: AndroidDocumentAccess
    ): DocumentAccess

    @Binds
    abstract fun bindDownloadNotifier(
        impl: AndroidDownloadNotifier
    ): DownloadNotifier

    @Binds
    abstract fun bindBriefingNotifier(
        impl: AndroidBriefingNotifier
    ): BriefingNotifier

    @Binds
    abstract fun bindReminderNotifier(
        impl: AndroidReminderNotifier
    ): ReminderNotifier

    @Binds
    abstract fun bindWidgetRefresher(
        impl: GlanceWidgetRefresher
    ): WidgetRefresher
    @Binds
    abstract fun bindCalendarTool(
        impl: AndroidCalendarTool
    ): CalendarTool

    @Binds
    abstract fun bindTemperatureProvider(
        impl: BatteryTemperatureProvider
    ): TemperatureProvider

    @Binds
    abstract fun bindDeviceResourceProvider(
        impl: AndroidDeviceResourceProvider
    ): DeviceResourceProvider

    // [WHY] 싱글턴 AppLanguageHolder 하나를 CurrentLanguage 읽기 전용 창구로도 노출한다 (0.40.3 M1).
    @Binds
    abstract fun currentLanguage(
        holder: AppLanguageHolder
    ): CurrentLanguage
}
