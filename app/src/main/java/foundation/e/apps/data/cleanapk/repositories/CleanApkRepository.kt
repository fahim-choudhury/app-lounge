/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.cleanapk.repositories

import foundation.e.apps.data.StoreRepository
import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response

const val NUMBER_OF_ITEMS = 20

const val NUMBER_OF_PAGES = 1
interface CleanApkRepository : StoreRepository {
    suspend fun getSearchResult(query: String, searchBy: String? = null): Response<Search>
    suspend fun getAppsByCategory(category: String, paginationParameter: Any? = null): Response<Search>
    suspend fun getCategories(): Response<Categories>
    suspend fun checkAvailablePackages(packageNames: List<String>): Response<Search>
    suspend fun getAppDetailsById(appId: String): Result<Application>
}
