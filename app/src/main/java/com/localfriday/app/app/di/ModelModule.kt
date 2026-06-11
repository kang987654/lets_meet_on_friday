package com.localfriday.app.app.di

import com.localfriday.app.domain.modelrunner.ModelRunner
import com.localfriday.app.domain.tool.Tokenizer
import com.localfriday.app.runtime.gemma.GemmaModelRunner
import com.localfriday.app.runtime.gemma.GemmaTokenizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelModule {
    @Binds
    abstract fun bindModelRunner(
        impl: GemmaModelRunner
    ): ModelRunner

    @Binds
    abstract fun bindTokenizer(
        impl: GemmaTokenizer
    ): Tokenizer
}
