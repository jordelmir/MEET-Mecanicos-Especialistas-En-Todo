package com.elysium369.meet.di

import android.content.Context
import com.elysium369.meet.core.knowledge.graph.AutomotiveKnowledgeGraphRepository
import com.elysium369.meet.domain.diagnostics.DiagnosticFindingRepository
import com.elysium369.meet.domain.diagnostics.RoomDiagnosticFindingRepository
import dagger.Module
import dagger.Binds
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticFindingRepository(
        implementation: RoomDiagnosticFindingRepository,
    ): DiagnosticFindingRepository

    companion object {
        @Provides
        @Singleton
        fun provideAutomotiveKnowledgeGraphRepository(
            @ApplicationContext context: Context,
        ): AutomotiveKnowledgeGraphRepository = AutomotiveKnowledgeGraphRepository(context)
    }
}
