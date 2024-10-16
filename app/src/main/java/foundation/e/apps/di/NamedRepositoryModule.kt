/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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
 */

package foundation.e.apps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.cleanapk.CleanApkAppDetailsRetrofit
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.repositories.CleanApkAppsRepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkPWARepository
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.playstore.PlayStoreRepositoryImpl
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import foundation.e.apps.data.login.AuthenticatorRepository
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NamedRepositoryModule {
    @Singleton
    @Provides
    @Named("gplayRepository")
    fun getGplayRepository(
        @ApplicationContext context: Context,
        gPlayHttpClient: GPlayHttpClient,
        authenticatorRepository: AuthenticatorRepository
    ): PlayStoreRepository {
        return PlayStoreRepositoryImpl(context, gPlayHttpClient, authenticatorRepository)
    }

    @Singleton
    @Provides
    @Named("cleanApkAppsRepository")
    fun getCleanApkAppsRepository(
        cleanAPKRetrofit: CleanApkRetrofit,
        cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
    ): CleanApkRepository {
        return CleanApkAppsRepository(cleanAPKRetrofit, cleanApkAppDetailsRetrofit)
    }

    @Singleton
    @Provides
    @Named("cleanApkPWARepository")
    fun getCleanApkPWARepository(
        cleanAPKRetrofit: CleanApkRetrofit,
        cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
    ): CleanApkRepository {
        return CleanApkPWARepository(cleanAPKRetrofit, cleanApkAppDetailsRetrofit)
    }
}
