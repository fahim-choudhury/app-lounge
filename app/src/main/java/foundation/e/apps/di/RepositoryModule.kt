// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.exodus.repositories.AppPrivacyInfoRepositoryImpl
import foundation.e.apps.data.exodus.repositories.IAppPrivacyInfoRepository
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepository
import foundation.e.apps.data.exodus.repositories.PrivacyScoreRepositoryImpl
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.fdroid.IFdroidRepository
import foundation.e.apps.data.fused.FusedApi
import foundation.e.apps.data.fused.FusedApiImpl
import foundation.e.apps.data.fusedDownload.FusedManagerImpl
import foundation.e.apps.data.fusedDownload.IFusedManager
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

    @Singleton
    @Binds
    fun getFusedApi(fusedApiImpl: FusedApiImpl): FusedApi

    @Singleton
    @Binds
    fun getPrivacyScoreRepository(privacyScoreRepositoryImpl: PrivacyScoreRepositoryImpl): PrivacyScoreRepository
}
