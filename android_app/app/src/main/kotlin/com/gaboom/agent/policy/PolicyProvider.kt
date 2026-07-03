package com.gaboom.agent.policy

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the default policy objects as singletons for Dependency Injection using Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object PolicyProvider {

    @Provides
    @Singleton
    fun provideOfflinePolicy(): OfflinePolicy = OfflinePolicy()

    @Provides
    @Singleton
    fun provideClockPolicy(): ClockPolicy = ClockPolicy()

    @Provides
    @Singleton
    fun provideSecurityPolicy(): SecurityPolicy = SecurityPolicy()

    @Provides
    @Singleton
    fun provideSyncPolicy(): SyncPolicy = SyncPolicy()

    @Provides
    @Singleton
    fun provideOfflineBudgetPolicy(): OfflineBudgetPolicy = OfflineBudgetPolicy()
}
