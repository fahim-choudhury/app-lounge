/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

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
import foundation.e.apps.data.fusedDownload.FusedManagerImpl
import foundation.e.apps.data.fusedDownload.IFusedManager
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.playstore.PlayStoreRepositoryImpl
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
    fun getPrivacyScoreRepository(privacyScoreRepositoryImpl: PrivacyScoreRepositoryImpl): PrivacyScoreRepository

    @Singleton
    @Binds
    fun getPlayStoreRepository(playStoreRepository: PlayStoreRepositoryImpl): PlayStoreRepository
}
