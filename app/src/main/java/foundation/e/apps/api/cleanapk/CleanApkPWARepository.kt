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
import foundation.e.apps.api.cleanapk.data.categories.Categories
import foundation.e.apps.api.cleanapk.data.search.Search
import foundation.e.apps.api.fused.utils.CategoryType
import retrofit2.Response

class CleanApkPWARepository(private val cleanAPKInterface: CleanAPKInterface) : StoreRepository {

    override suspend fun getHomeScreenData(): Any {
        return cleanAPKInterface.getHomeScreenData(
            CleanAPKInterface.APP_TYPE_PWA,
            CleanAPKInterface.APP_SOURCE_ANY
        )
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanAPKInterface.searchApps(
            query,
            CleanAPKInterface.APP_SOURCE_ANY,
            CleanAPKInterface.APP_TYPE_PWA,
            20,
            1,
            searchBy
        )
    }

    override suspend fun getSearchSuggestions(query: String): Any {
        TODO("Not yet implemented")
    }

    override suspend fun getAppsByCategory(category: String, paginationParameter: Any?): Any {
        return cleanAPKInterface.listApps(
            category,
            CleanAPKInterface.APP_SOURCE_FOSS,
            CleanAPKInterface.APP_TYPE_PWA,
            20,
            1
        )
    }

    override suspend fun getCategories(type: CategoryType?): Response<Categories> {
        return cleanAPKInterface.getCategoriesList(
            CleanAPKInterface.APP_TYPE_PWA,
            CleanAPKInterface.APP_SOURCE_ANY
        )
    }
}
