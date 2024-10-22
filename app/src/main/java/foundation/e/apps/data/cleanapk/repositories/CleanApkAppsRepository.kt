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

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.cleanapk.CleanApkDownloadInfoFetcher
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.data.app.CleanApkApplication
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.cleanapk.data.home.CleanApkHome
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response
import javax.inject.Inject

class CleanApkAppsRepository @Inject constructor(
    private val cleanApkRetrofit: CleanApkRetrofit,
    private val applicationDataManager: ApplicationDataManager,
    @ApplicationContext val context: Context
) : CleanApkRepository, CleanApkDownloadInfoFetcher {

    override suspend fun getHomeScreenData(): Map<String, List<Application>> {

        val response = cleanApkRetrofit.getHomeScreenData(
            CleanApkRetrofit.APP_TYPE_ANY,
            CleanApkRetrofit.APP_SOURCE_FOSS
        )

        val home = response.body()?.home ?: throw IllegalStateException("No home data found")

        val listHome = toGenericHome(home, CleanApkRetrofit.APP_TYPE_ANY)
        val map = mutableMapOf<String, List<Application>>()
        listHome.forEach {
            map[it.title] = it.list
        }

        return map
    }

    private suspend fun toGenericHome(cleanApkHome: CleanApkHome, appType: String): List<Home> {
        val list = mutableListOf<Home>()

        openSourceCategories.forEach { (key, value) ->
            when (key) {
                "top_updated_apps" -> {
                    applicationDataManager.prepareApps(cleanApkHome.top_updated_apps, list, value)
                }

                "top_updated_games" -> {
                    applicationDataManager.prepareApps(cleanApkHome.top_updated_games, list, value)
                }

                "popular_apps" -> {
                    applicationDataManager.prepareApps(cleanApkHome.popular_apps, list, value)
                }

                "popular_games" -> {
                    applicationDataManager.prepareApps(cleanApkHome.popular_games, list, value)
                }

                "popular_apps_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(
                        cleanApkHome.popular_apps_in_last_24_hours,
                        list,
                        value
                    )
                }

                "popular_games_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(
                        cleanApkHome.popular_games_in_last_24_hours,
                        list,
                        value
                    )
                }

                "discover" -> {
                    applicationDataManager.prepareApps(cleanApkHome.discover, list, value)
                }
            }
        }

        return list.map {
            it.source = appType
            it
        }
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanApkRetrofit.searchApps(
            query,
            CleanApkRetrofit.APP_SOURCE_FOSS,
            CleanApkRetrofit.APP_TYPE_ANY,
            NUMBER_OF_ITEMS,
            NUMBER_OF_PAGES,
            searchBy
        )
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

    override suspend fun getAppDetails(packageNameOrId: String): Response<CleanApkApplication> {
        return cleanApkRetrofit.getAppOrPWADetailsByID(packageNameOrId, null, null)
    }

    override suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any?): Response<Download> {
        val version = versionCode?.let { it as String }
        return cleanApkRetrofit.getDownloadInfo(idOrPackageName, version, null)
    }

    private val openSourceCategories: Map<String, String> by lazy {
        mapOf(
            "top_updated_apps" to context.getString(R.string.top_updated_apps),
            "top_updated_games" to context.getString(R.string.top_updated_games),
            "popular_apps_in_last_24_hours" to context.getString(R.string.popular_apps_in_last_24_hours),
            "popular_games_in_last_24_hours" to context.getString(R.string.popular_games_in_last_24_hours),
            "discover" to context.getString(R.string.discover)
        )
    }
}
