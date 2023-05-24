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

import foundation.e.apps.data.cleanapk.CleanApkAppDetailsRetrofit
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response

class CleanApkPWARepository(
    private val cleanAPKRetrofit: CleanApkRetrofit,
    private val cleanApkAppDetailsRetrofit: CleanApkAppDetailsRetrofit
) : CleanApkRepository {

    override suspend fun getHomeScreenData(): Any {
        return cleanAPKRetrofit.getHomeScreenData(
            CleanApkRetrofit.APP_TYPE_PWA,
            CleanApkRetrofit.APP_SOURCE_ANY
        )
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanAPKRetrofit.searchApps(
            query,
            CleanApkRetrofit.APP_SOURCE_ANY,
            CleanApkRetrofit.APP_TYPE_PWA,
            20,
            1,
            searchBy
        )
    }

    override suspend fun getAppsByCategory(category: String, paginationParameter: Any?): Response<Search> {
        return cleanAPKRetrofit.listApps(
            category,
            CleanApkRetrofit.APP_SOURCE_ANY,
            CleanApkRetrofit.APP_TYPE_PWA,
            NUMBER_OF_ITEMS,
            NUMBER_OF_PAGES
        )
    }

    override suspend fun getCategories(): Response<Categories> {
        return cleanAPKRetrofit.getCategoriesList(
            CleanApkRetrofit.APP_TYPE_PWA,
            CleanApkRetrofit.APP_SOURCE_ANY
        )
    }

    override suspend fun getAppDetails(packageNameOrId: String): Response<Application> {
        return cleanApkAppDetailsRetrofit.getAppOrPWADetailsByID(packageNameOrId, null, null)
    }
}
