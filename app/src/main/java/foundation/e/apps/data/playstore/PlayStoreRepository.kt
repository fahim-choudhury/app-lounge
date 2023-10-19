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

package foundation.e.apps.data.playstore

import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.StoreRepository
import foundation.e.apps.data.fused.utils.CategoryType

interface PlayStoreRepository : StoreRepository {
    suspend fun getSearchResult(query: String, subBundle: MutableSet<SearchBundle.SubBundle>?): Pair<List<App>, MutableSet<SearchBundle.SubBundle>>
    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry>
    suspend fun getAppsByCategory(category: String, pageUrl: String? = null): Any
    suspend fun getCategories(type: CategoryType? = null): List<Category>
    suspend fun getAppsDetails(packageNamesOrIds: List<String>): List<App>
    suspend fun getDownloadInfo(
        idOrPackageName: String,
        versionCode: Any? = null,
        offerType: Int = -1
    ): List<File>
    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): List<File>
}
