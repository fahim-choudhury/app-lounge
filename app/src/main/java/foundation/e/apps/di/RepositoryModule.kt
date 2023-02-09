package foundation.e.apps.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.api.exodus.repositories.AppPrivacyInfoRepositoryImpl
import foundation.e.apps.api.exodus.repositories.IAppPrivacyInfoRepository
import foundation.e.apps.api.fdroid.FdroidRepository
import foundation.e.apps.api.fdroid.IFdroidRepository
import foundation.e.apps.manager.fused.FusedManagerImpl
import foundation.e.apps.manager.fused.IFusedManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Singleton
    @Binds
    fun getRepositoryModule(trackerRepositoryImpl: AppPrivacyInfoRepositoryImpl): IAppPrivacyInfoRepository

    @Singleton
    @Binds
    fun getFusedManagerImpl(fusedManagerImpl: FusedManagerImpl): IFusedManager

    @Singleton
    @Binds
    fun getFdroidRepository(fusedManagerImpl: FdroidRepository): IFdroidRepository
}
