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

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.application.AppsApi
import foundation.e.apps.data.application.AppsApiImpl
import foundation.e.apps.data.application.CategoryApi
import foundation.e.apps.data.application.CategoryApiImpl
import foundation.e.apps.data.application.DownloadInfoApi
import foundation.e.apps.data.application.DownloadInfoApiImpl
import foundation.e.apps.data.application.HomeApi
import foundation.e.apps.data.application.HomeApiImpl
import foundation.e.apps.data.application.SearchApi
import foundation.e.apps.data.application.SearchApiImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Singleton
    @Binds
    fun getHomeApi(homeApiImpl: HomeApiImpl): HomeApi

    @Singleton
    @Binds
    fun getCategoryApi(categoryApiImpl: CategoryApiImpl): CategoryApi

    @Singleton
    @Binds
    fun getAppsApi(appsApiImpl: AppsApiImpl): AppsApi

    @Singleton
    @Binds
    fun getSearchApi(searchApi: SearchApiImpl): SearchApi

    @Singleton
    @Binds
    fun getDownloadInfoApi(downloadInfoApi: DownloadInfoApiImpl): DownloadInfoApi
}
