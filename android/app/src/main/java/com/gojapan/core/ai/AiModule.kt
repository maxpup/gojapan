package com.gojapan.core.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindAiChatClient(implementation: OpenAiCompatibleChatClient): AiChatClient

    @Binds
    @Singleton
    abstract fun bindAiApiKeyStore(implementation: AndroidAiApiKeyStore): AiApiKeyStore
}
