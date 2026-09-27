package com.elysium369.meet.core.agentstore.di

import com.elysium369.meet.core.agentstore.data.PlayStoreAgentPurchaseCoordinator
import com.elysium369.meet.core.agentstore.data.SupabaseAgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.AgentPurchaseCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentStoreModule {

    @Binds
    @Singleton
    abstract fun bindAgentEntitlementGateway(
        impl: SupabaseAgentEntitlementGateway,
    ): AgentEntitlementGateway

    @Binds
    @Singleton
    abstract fun bindAgentPurchaseCoordinator(
        impl: PlayStoreAgentPurchaseCoordinator,
    ): AgentPurchaseCoordinator
}
