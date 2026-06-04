package com.localfriday.app.app.di

import com.localfriday.app.domain.tool.SpeechToTextTool
import com.localfriday.app.platform.speech.AndroidSpeechToTextTool
import com.localfriday.app.domain.tool.CalendarTool
import com.localfriday.app.platform.calendar.AndroidCalendarTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    abstract fun bindSpeechToTextTool(
        impl: AndroidSpeechToTextTool
    ): SpeechToTextTool

    @Binds
    abstract fun bindCalendarTool(
        impl: AndroidCalendarTool
    ): CalendarTool

    @Binds
    abstract fun bindWebSearchTool(
        impl: com.localfriday.app.platform.network.WebSearchGateway
    ): com.localfriday.app.domain.tool.WebSearchTool

    @Binds
    abstract fun bindTemperatureProvider(
        impl: com.localfriday.app.platform.device.BatteryTemperatureProvider
    ): com.localfriday.app.platform.device.TemperatureProvider
}
