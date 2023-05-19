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

package foundation.e.apps.data.cleanapk

import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.cleanapk.data.home.HomeScreen
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response

class CleanApkAppsRepositoryImpl(
    private val cleanApkRetrofit: CleanApkRetrofit,
    private val cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
) : CleanApkRepository, CleanApkDownloadInfoFetcher{

    override suspend fun getHomeScreenData(): Response<HomeScreen> {
        return cleanApkRetrofit.getHomeScreenData(
            CleanApkRetrofit.APP_TYPE_ANY,
            CleanApkRetrofit.APP_SOURCE_FOSS
        )
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanApkRetrofit.searchApps(
            query,
            CleanApkRetrofit.APP_SOURCE_FOSS,
            CleanApkRetrofit.APP_TYPE_ANY,
            20,
            1,
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
            20,
            1
        )
    }

    override suspend fun getCategories(): Response<Categories> {
        return cleanApkRetrofit.getCategoriesList(
            CleanApkRetrofit.APP_TYPE_ANY,
            CleanApkRetrofit.APP_SOURCE_FOSS
        )
    }

    override suspend fun getAppDetails(packageNameOrId: String): Response<Application> {
        return cleanApkAppDetailsRetrofit.getAppOrPWADetailsByID(packageNameOrId, null, null)
    }

    override suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any?): Response<Download> {
        val version = versionCode?.let { it as String }
        return cleanApkRetrofit.getDownloadInfo(idOrPackageName, version, null)
    }
}
