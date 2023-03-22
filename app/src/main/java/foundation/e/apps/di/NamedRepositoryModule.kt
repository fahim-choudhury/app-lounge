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
import foundation.e.apps.api.StoreRepository
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.cleanapk.CleanApkAppDetailApi
import foundation.e.apps.api.cleanapk.CleanApkAppsRepository
import foundation.e.apps.api.cleanapk.CleanApkPWARepository
import foundation.e.apps.api.gplay.GplayRepository
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import foundation.e.apps.login.LoginSourceRepository
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
        loginSourceRepository: LoginSourceRepository
    ): StoreRepository {
        return GplayRepository(context, gPlayHttpClient, loginSourceRepository)
    }

    @Singleton
    @Provides
    @Named("cleanApkAppsRepository")
    fun getCleanApkAppsRepository(
        cleanAPKInterface: CleanAPKInterface,
    ): StoreRepository {
        return CleanApkAppsRepository(cleanAPKInterface)
    }

    @Singleton
    @Provides
    @Named("cleanApkPWARepository")
    fun getCleanApkPWARepository(
        cleanAPKInterface: CleanAPKInterface,
    ): StoreRepository {
        return CleanApkPWARepository(cleanAPKInterface)
    }
}
