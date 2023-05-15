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

package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.DownloadInfoFetcher
import foundation.e.apps.api.StoreRepository
import foundation.e.apps.api.cleanapk.data.app.Application
import foundation.e.apps.api.cleanapk.data.categories.Categories
import foundation.e.apps.api.cleanapk.data.download.Download
import foundation.e.apps.api.cleanapk.data.home.HomeScreen
import foundation.e.apps.api.cleanapk.data.search.Search
import foundation.e.apps.api.fused.utils.CategoryType
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import retrofit2.Response

class CleanApkAppsRepository(
    private val cleanAPKInterface: CleanAPKInterface,
    private val cleanApkAppDetailApi: CleanApkAppDetailApi
) : StoreRepository, DownloadInfoFetcher {

    override suspend fun getHomeScreenData(): Response<HomeScreen> {
        return cleanAPKInterface.getHomeScreenData(
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS
        )
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanAPKInterface.searchApps(
            query,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_ANY,
            20,
            1,
            searchBy
        )
    }

    override suspend fun getSearchSuggestions(query: String): Any {
        return Any()
    }

    override suspend fun getAppsByCategory(
        category: String,
        paginationParameter: Any?
    ): Response<Search> {
        return cleanAPKInterface.listApps(
            category,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_ANY,
            20,
            1
        )
    }

    override suspend fun getCategories(type: CategoryType?): Response<Categories> {
        return cleanAPKInterface.getCategoriesList(
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS
        )
    }

    override suspend fun getAppDetails(packageNameOrId: String): Response<Application> {
        return cleanApkAppDetailApi.getAppOrPWADetailsByID(packageNameOrId, null, null)
    }

    override suspend fun getAppsDetails(packageNamesOrIds: List<String>): Any {
        val applications = mutableListOf<Application>()

        packageNamesOrIds.forEach {
            val applicationResponse = getAppDetails(it)
            if (applicationResponse.isSuccessful && applicationResponse.body() != null) {
                applications.add(applicationResponse.body()!!)
            }
        }
        return applications
    }

    override suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any?, offerType: Int): Response<Download> {
        val version = versionCode?.let { it as String }
        return cleanAPKInterface.getDownloadInfo(idOrPackageName, version, null)
    }
}
