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
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.apps.AppsApiImpl
import foundation.e.apps.data.application.category.CategoryApi
import foundation.e.apps.data.application.category.CategoryApiImpl
import foundation.e.apps.data.application.downloadInfo.DownloadInfoApi
import foundation.e.apps.data.application.downloadInfo.DownloadInfoApiImpl
import foundation.e.apps.data.application.home.HomeApi
import foundation.e.apps.data.application.home.HomeApiImpl
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.application.search.SearchApiImpl
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
