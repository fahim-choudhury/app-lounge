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

package foundation.e.apps.data.cleanapk.repositories

import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.enums.Source
import retrofit2.Response
import javax.inject.Inject

class CleanApkAppsRepository @Inject constructor(
    private val cleanApkRetrofit: CleanApkRetrofit,
    private val homeConverter: HomeConverter
) : CleanApkRepository, CleanApkDownloadInfoFetcher {

    override suspend fun getHomeScreenData(list: MutableList<Home>): List<Home> {

        val response = cleanApkRetrofit.getHomeScreenData(
            CleanApkRetrofit.APP_TYPE_ANY,
            CleanApkRetrofit.APP_SOURCE_FOSS
        )

        val home = response.body()?.home ?: throw IllegalStateException("No home data found")
        val listHome = homeConverter.toGenericHome(home, CleanApkRetrofit.APP_SOURCE_FOSS)
        val map = mutableMapOf<String, List<Application>>()
        listHome.forEach {
            map[it.title] = it.list
        }

        listHome.forEach { (title, apps) ->
            apps.forEach { app -> app.source = Source.OPEN_SOURCE }
            list.add(Home(title, apps, SearchApi.APP_TYPE_OPEN))
        }

        return list
    }

    override suspend fun getAppsByCategory(
        category: String,
        paginationParameter: Any?
    ): Response<Search> {
        return cleanApkRetrofit.listApps(
            category,
            CleanApkRetrofit.APP_SOURCE_FOSS,
            CleanApkRetrofit.APP_TYPE_ANY,
            NUMBER_OF_ITEMS,
            NUMBER_OF_PAGES
        )
    }

    override suspend fun getCategories(): Response<Categories> {
        return cleanApkRetrofit.getCategoriesList(
            CleanApkRetrofit.APP_TYPE_ANY,
            CleanApkRetrofit.APP_SOURCE_FOSS
        )
    }

    override suspend fun checkAvailablePackages(packageNames: List<String>): Response<Search> {
        return cleanApkRetrofit.checkAvailablePackages(packageNames)
    }

    override suspend fun getAppDetails(packageName: String): Application {
        val apps = cleanApkRetrofit.checkAvailablePackages(listOf(packageName))
        val app = apps.body()?.apps?.firstOrNull() ?: return Application()
        val response = cleanApkRetrofit.getAppOrPWADetailsByID(app._id, null, null)
        return response.body()?.app ?: return Application()
    }

    override suspend fun getSearchResults(pattern: String): List<Application> {
        val searchResult = cleanApkRetrofit.searchApps(
            pattern,
            CleanApkRetrofit.APP_SOURCE_FOSS,
            CleanApkRetrofit.APP_TYPE_NATIVE,
            NUMBER_OF_ITEMS,
            NUMBER_OF_PAGES
        )

        val apps = searchResult.body()?.apps
        apps?.forEach { app ->
            app.source = if (app.is_pwa) {
                Source.PWA
            } else {
                Source.OPEN_SOURCE
            }
        }

        return apps ?: emptyList()
    }

    override suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any?): Response<Download> {
        val version = versionCode?.let { it as String }
        return cleanApkRetrofit.getDownloadInfo(idOrPackageName, version, null)
    }
}
