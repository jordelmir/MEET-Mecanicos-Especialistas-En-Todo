package com.elysium369.meet.di

import com.elysium369.meet.core.parts.CompatibilityEngine
import com.elysium369.meet.core.parts.PartQuoteRanker
import com.elysium369.meet.core.parts.PartSuggestionEngine
import com.elysium369.meet.core.parts.QuoteValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PartsModule {

    @Provides
    @Singleton
    fun provideCompatibilityEngine(): CompatibilityEngine = CompatibilityEngine

    @Provides
    @Singleton
    fun providePartQuoteRanker(): PartQuoteRanker = PartQuoteRanker

    @Provides
    @Singleton
    fun provideQuoteValidator(): QuoteValidator = QuoteValidator

    @Provides
    @Singleton
    fun providePartSuggestionEngine(): PartSuggestionEngine = PartSuggestionEngine
}
