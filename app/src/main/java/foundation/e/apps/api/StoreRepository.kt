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

package foundation.e.apps.api

import foundation.e.apps.api.fused.utils.CategoryType

interface StoreRepository {
    suspend fun getHomeScreenData(): Any
    suspend fun getSearchResult(query: String, searchBy: String? = null): Any
    suspend fun getSearchSuggestions(query: String): Any
    suspend fun getAppsByCategory(category: String, paginationParameter: Any? = null): Any
    suspend fun getCategories(type: CategoryType? = null): Any
    suspend fun getAppDetails(packageNameOrId: String): Any?
    suspend fun getAppsDetails(packageNamesOrIds: List<String>): Any
}
