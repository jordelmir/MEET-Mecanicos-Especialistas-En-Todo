package com.elysium369.meet.di

import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.reports.ReportHashingService
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring the report-hashing surface (singletons).
 *
 * Why @Provides + @Singleton even though ReportHashingService is itself
 * `@Singleton @Inject`?  Two reasons:
 *   1. [HashEngine] is a Kotlin `object` — Hilt can't constructor-inject it.
 *      We bridge it through a trivial provider so future consumers can
 *      `inject HashEngineWrapper` instead of touching the global object.
 *   2. Having an explicit module keeps the surface auditable. When the
 *      parity contract changes (e.g. TS adds a new privacy field), one
 *      diff here tells the reviewer the whole story.
 *
 * Both bindings live in the SingletonComponent so they survive Activity
 * recreation and ViewModel scoping.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportsModule {

    /**
     * Re-export the HashEngine singleton under the Hilt graph. Cheap
     * indirection — the engine is stateless, but pinning it here gives
     * us one place to swap in a fake during Espresso UI tests.
     */
    @Provides
    @Singleton
    fun provideHashEngine(): HashEngine = HashEngine

    /**
     * ReportHashingService already declares `@Inject constructor()`. We
     * don't strictly need a manual @Provides here — but having one makes
     * the binding grep-able alongside provideHashEngine.
     */
    @Provides
    @Singleton
    fun provideReportHashingService(): ReportHashingService = ReportHashingService()
}

/**
 * Hilt entry point used by Compose screens that don't have access to a
 * `@HiltViewModel` for [ReportHashingService]. Use
 * `EntryPointAccessors.fromApplication(context, ReportsEntryPoint::class.java)`
 * inside a `@Composable` `remember { ... }` block.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReportsEntryPoint {
    fun reportHashingService(): ReportHashingService
    fun hashEngine(): HashEngine
}
