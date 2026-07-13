package com.kosmos.app.di

import com.kosmos.app.domain.tool.SpeechToTextTool
import com.kosmos.app.platform.speech.AndroidSpeechToTextTool
import com.kosmos.app.domain.tool.CalendarTool
import com.kosmos.app.platform.calendar.AndroidCalendarTool
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
        impl: com.kosmos.app.platform.network.WebSearchGateway
    ): com.kosmos.app.domain.tool.WebSearchTool

    @Binds
    abstract fun bindTemperatureProvider(
        impl: com.kosmos.app.platform.device.BatteryTemperatureProvider
    ): com.kosmos.app.platform.device.TemperatureProvider
}
