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

import foundation.e.apps.api.StoreRepository
import foundation.e.apps.api.cleanapk.data.home.HomeScreen
import foundation.e.apps.api.cleanapk.data.search.Search
import retrofit2.Response

class CleanApkAppsRepository(
    private val cleanAPKInterface: CleanAPKInterface,
    private val cleanApkAppDetailApi: CleanApkAppDetailApi
) : StoreRepository {

    override suspend fun getHomeScreenData(): Response<HomeScreen> {
        return cleanAPKInterface.getHomeScreenData(
            CleanAPKInterface.APP_TYPE_ANY,
            CleanAPKInterface.APP_SOURCE_FOSS
        )
    }

    override suspend fun getSearchResult(query: String): Response<Search> {
        return cleanAPKInterface.searchApps(
            query,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_ANY,
            20,
            1,
            null
        )
    }

    override suspend fun getSearchSuggestions(query: String): Any {
        return Any()
    }

    override suspend fun getAppsByCategory(category: String, paginationParameter: Any?): Response<Search> {
        return cleanAPKInterface.listApps(
            category,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_ANY,
            20,
            1
        )
    }
}
