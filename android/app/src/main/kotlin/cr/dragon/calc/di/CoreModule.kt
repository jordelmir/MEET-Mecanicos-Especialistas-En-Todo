package cr.dragon.calc.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.cas.CalculusEngine
import dragoncore.cas.SymbolicOptimizer
import dragoncore.lexer.DragonLexer
import dragoncore.physics.DragonPhysicsEngine
import dragoncore.security.DragonSandbox
import cr.dragon.calc.data.storage.DragonDatabase
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * Elysium Vanguard — CoreModule
 *
 * Módulo Hilt que provee instancias Singleton del motor matemático.
 * Instalado en SingletonComponent → viven durante toda la app.
 *
 * Nota: DragonParser NO es singleton porque requiere una lista de tokens
 * como parámetro de constructor (se crea por evaluación).
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDragonLexer(): DragonLexer = DragonLexer()

    @Provides
    @Singleton
    fun provideDragonEvaluator(): DragonEvaluator = DragonEvaluator()

    @Provides
    @Singleton
    fun provideDragonContext(): DragonContext = DragonContext()

    @Provides
    @Singleton
    fun provideSymbolicOptimizer(): SymbolicOptimizer = SymbolicOptimizer()

    @Provides
    @Singleton
    fun provideCalculusEngine(): CalculusEngine = CalculusEngine()

    @Provides
    @Singleton
    fun providePhysicsEngine(): DragonPhysicsEngine = DragonPhysicsEngine()

    @Provides
    fun provideDatabase(@ApplicationContext context: Context): DragonDatabase = DragonDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideDragonSandbox(evaluator: DragonEvaluator): DragonSandbox = DragonSandbox(evaluator)

    @Provides
    @Singleton
    fun provideNeuralVault(@ApplicationContext context: Context): dragoncore.security.NeuralVault = dragoncore.security.NeuralVault(context)

    @Provides
    @Singleton
    fun provideNeuralRouter(
        @ApplicationContext context: Context,
        vault: dragoncore.security.NeuralVault
    ): dragoncore.neural.NeuralRouter {
        return dragoncore.neural.NeuralRouter(context, vault)
    }
}
