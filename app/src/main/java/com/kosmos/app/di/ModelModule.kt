package com.kosmos.app.di

import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.runtime.gemma.GemmaModelRunner
import com.kosmos.app.runtime.gemma.GemmaTokenizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import com.kosmos.app.domain.tool.ImageProcessor
import com.kosmos.app.runtime.gemma.ImageInputAdapter

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelModule {
    @Binds
    abstract fun bindModelLoadManager(
        impl: GemmaRuntimeManager
    ): ModelLoadManager

    @Binds
    abstract fun bindImageProcessor(
        impl: ImageInputAdapter
    ): ImageProcessor
    @Binds
    abstract fun bindModelRunner(
        impl: GemmaModelRunner
    ): ModelRunner

    @Binds
    abstract fun bindTokenizer(
        impl: GemmaTokenizer
    ): Tokenizer
}
