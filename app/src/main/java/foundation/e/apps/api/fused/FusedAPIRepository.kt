/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.api.fused

import androidx.lifecycle.LiveData
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedCategory
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedAPIRepository @Inject constructor(
    private val fusedAPIImpl: FusedAPIImpl
) {
    suspend fun getHomeScreenData(authData: AuthData): Pair<List<FusedHome>, String> {
        return fusedAPIImpl.getHomeScreenData(authData)
    }

    fun isFusedHomesEmpty(fusedHomes: List<FusedHome>): Boolean {
        return fusedAPIImpl.isFusedHomesEmpty(fusedHomes)
    }

    suspend fun validateAuthData(authData: AuthData): Boolean {
        return fusedAPIImpl.validateAuthData(authData)
    }

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): List<FusedApp> {
        return fusedAPIImpl.getApplicationDetails(packageNameList, authData, origin)
    }

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): FusedApp {
        return fusedAPIImpl.getApplicationDetails(id, packageName, authData, origin)
    }

    suspend fun updateFusedDownloadWithDownloadingInfo(
        authData: AuthData,
        origin: Origin,
        fusedDownload: FusedDownload
    ) {
        fusedAPIImpl.updateFusedDownloadWithDownloadingInfo(
            authData,
            origin,
            fusedDownload
        )
    }

    suspend fun getCategoriesList(type: Category.Type, authData: AuthData): List<FusedCategory> {
        return fusedAPIImpl.getCategoriesList(type, authData)
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        return fusedAPIImpl.getSearchSuggestions(query, authData)
    }

    suspend fun fetchAuthData(): Unit? {
        return fusedAPIImpl.fetchAuthData()
    }

    suspend fun fetchAuthData(email: String, aasToken: String): AuthData {
        return fusedAPIImpl.fetchAuthData(email, aasToken)
    }

    fun getSearchResults(query: String, authData: AuthData): LiveData<List<FusedApp>> {
        return fusedAPIImpl.getSearchResults(query, authData)
    }

    suspend fun listApps(category: String, browseUrl: String, authData: AuthData): List<FusedApp>? {
        return fusedAPIImpl.listApps(category, browseUrl, authData)
    }

    suspend fun getPlayStoreAppCategoryUrls(browseUrl: String, authData: AuthData): List<String> {
        return fusedAPIImpl.getPlayStoreAppCategoryUrls(browseUrl, authData)
    }

    suspend fun getAppsAndNextClusterUrl(browseUrl: String, authData: AuthData): Pair<List<FusedApp>, String> {
        return fusedAPIImpl.getAppsAndNextClusterUrl(browseUrl, authData)
    }

    suspend fun getAppsListBasedOnCategory(
        category: String,
        browseUrl: String,
        authData: AuthData,
        source: String
    ): List<FusedApp> {
        return when (source) {
            "Open Source" -> fusedAPIImpl.getOpenSourceApps(category) ?: listOf()
            "PWA" -> fusedAPIImpl.getPWAApps(category) ?: listOf()
            else -> fusedAPIImpl.getPlayStoreApps(browseUrl, authData)
        }
    }

    fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status {
        return fusedAPIImpl.getFusedAppInstallationStatus(fusedApp)
    }
}
