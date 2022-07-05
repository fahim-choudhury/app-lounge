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
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedCategory
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.utils.enums.FilterLevel
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedAPIRepository @Inject constructor(
    private val fusedAPIImpl: FusedAPIImpl
) {
    suspend fun getHomeScreenData(authData: AuthData): Pair<List<FusedHome>, ResultStatus> {
        return fusedAPIImpl.getHomeScreenData(authData)
    }

    fun isFusedHomesEmpty(fusedHomes: List<FusedHome>): Boolean {
        return fusedAPIImpl.isFusedHomesEmpty(fusedHomes)
    }

    fun getApplicationCategoryPreference(): String {
        return fusedAPIImpl.getApplicationCategoryPreference()
    }

    suspend fun validateAuthData(authData: AuthData): Boolean {
        return authData.authToken.isNotEmpty() && authData.deviceInfoProvider != null && fusedAPIImpl.validateAuthData(
            authData
        )
    }

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<FusedApp>, ResultStatus> {
        return fusedAPIImpl.getApplicationDetails(packageNameList, authData, origin)
    }

    suspend fun filterRestrictedGPlayApps(
        authData: AuthData,
        appList: List<App>,
    ): ResultSupreme<List<FusedApp>> {
        return fusedAPIImpl.filterRestrictedGPlayApps(authData, appList)
    }

    suspend fun getAppFilterLevel(fusedApp: FusedApp, authData: AuthData?): FilterLevel {
        return fusedAPIImpl.getAppFilterLevel(fusedApp, authData)
    }

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<FusedApp, ResultStatus> {
        return fusedAPIImpl.getApplicationDetails(id, packageName, authData, origin)
    }

    suspend fun getCleanapkAppDetails(packageName: String): Pair<FusedApp, ResultStatus> {
        return fusedAPIImpl.getCleanapkAppDetails(packageName)
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

    suspend fun getCategoriesList(
        type: Category.Type,
        authData: AuthData
    ): Triple<List<FusedCategory>, String, ResultStatus> {
        return fusedAPIImpl.getCategoriesList(type, authData)
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        return fusedAPIImpl.getSearchSuggestions(query, authData)
    }

    suspend fun fetchAuthData(): Boolean {
        return fusedAPIImpl.fetchAuthData()
    }

    suspend fun fetchAuthData(email: String, aasToken: String): AuthData? {
        return fusedAPIImpl.fetchAuthData(email, aasToken)
    }

    fun getSearchResults(query: String, authData: AuthData): LiveData<ResultSupreme<Pair<List<FusedApp>, Boolean>>> {
        return fusedAPIImpl.getSearchResults(query, authData)
    }

    suspend fun getNextStreamBundle(
        authData: AuthData,
        homeUrl: String,
        currentStreamBundle: StreamBundle,
    ): ResultSupreme<StreamBundle> {
        return fusedAPIImpl.getNextStreamBundle(authData, homeUrl, currentStreamBundle)
    }

    suspend fun getAdjustedFirstCluster(
        authData: AuthData,
        streamBundle: StreamBundle,
        pointer: Int = 0,
    ): ResultSupreme<StreamCluster> {
        return fusedAPIImpl.getAdjustedFirstCluster(authData, streamBundle, pointer)
    }

    suspend fun getNextStreamCluster(
        authData: AuthData,
        currentStreamCluster: StreamCluster,
    ): ResultSupreme<StreamCluster> {
        return fusedAPIImpl.getNextStreamCluster(authData, currentStreamCluster)
    }

    suspend fun getAppsListBasedOnCategory(
        category: String,
        browseUrl: String,
        authData: AuthData,
        source: String
    ): ResultSupreme<List<FusedApp>> {
        return when (source) {
            "Open Source" -> fusedAPIImpl.getOpenSourceApps(category)
            "PWA" -> fusedAPIImpl.getPWAApps(category)
            else -> fusedAPIImpl.getPlayStoreApps(browseUrl, authData)
        }
    }

    fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status {
        return fusedAPIImpl.getFusedAppInstallationStatus(fusedApp)
    }
}
