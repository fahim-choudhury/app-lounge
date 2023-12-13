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

package foundation.e.apps.data.application.search

import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.data.Application

typealias GplaySearchResult = ResultSupreme<Pair<List<Application>, Set<SearchBundle.SubBundle>>>

interface SearchApi {
    companion object {
        const val APP_TYPE_ANY = "any"
        const val APP_TYPE_OPEN = "open"
        const val APP_TYPE_PWA = "pwa"
    }

    fun getSelectedAppTypes(): List<String>

    /**
     * Fetches search results from cleanAPK and GPlay servers and returns them
     * @param query Query
     * @param authData [AuthData]
     * @return ResultSupreme which contains a Pair<List<FusedApp>, Boolean> where List<FusedApp>
     *     is the app list and [Boolean] indicates more data to load or not.
     */
    suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<Application>, Boolean>>

    suspend fun getGplaySearchResult(
        query: String,
        nextPageSubBundle: Set<SearchBundle.SubBundle>?
    ): GplaySearchResult

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry>
}
