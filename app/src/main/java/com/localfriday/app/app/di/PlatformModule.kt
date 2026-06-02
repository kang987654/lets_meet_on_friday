package com.localfriday.app.app.di

import com.localfriday.app.domain.tool.SpeechToTextTool
import com.localfriday.app.platform.speech.AndroidSpeechToTextTool
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
}
