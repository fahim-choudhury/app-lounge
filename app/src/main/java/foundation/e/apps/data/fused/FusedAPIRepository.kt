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

package foundation.e.apps.data.fused

import androidx.lifecycle.LiveData
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedCategory
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedAPIRepository @Inject constructor(private val fusedAPIImpl: FusedApi) {

    suspend fun getHomeScreenData(authData: AuthData): LiveData<ResultSupreme<List<FusedHome>>> {
        return fusedAPIImpl.getHomeScreenData(authData)
    }

    fun getApplicationCategoryPreference(): List<String> {
        return fusedAPIImpl.getApplicationCategoryPreference()
    }

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<FusedApp>, ResultStatus> {
        return fusedAPIImpl.getApplicationDetails(packageNameList, authData, origin)
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
        origin: Origin,
        fusedDownload: FusedDownload
    ) {
        fusedAPIImpl.updateFusedDownloadWithDownloadingInfo(
            origin,
            fusedDownload
        )
    }

    suspend fun getOSSDownloadInfo(id: String, version: String? = null) =
        fusedAPIImpl.getOSSDownloadInfo(id, version)

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String? {
        return fusedAPIImpl.getOnDemandModule(packageName, moduleName, versionCode, offerType)
    }

    suspend fun getCategoriesList(
        type: CategoryType,
    ): Triple<List<FusedCategory>, String, ResultStatus> {
        return fusedAPIImpl.getCategoriesList(type)
    }

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        return fusedAPIImpl.getSearchSuggestions(query)
    }

    suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): ResultSupreme<Pair<List<FusedApp>, Boolean>> {
        return fusedAPIImpl.getCleanApkSearchResults(query, authData)
    }

    suspend fun getGplaySearchResults(
        query: String,
        nextPageSubBundle: Set<SearchBundle.SubBundle>?
    ): GplaySearchResult {
        return fusedAPIImpl.getGplaySearchResult(query, nextPageSubBundle)
    }

    suspend fun getAppsListBasedOnCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?,
        source: Source
    ): ResultSupreme<Pair<List<FusedApp>, String>> {
        return when (source) {
            Source.OPEN -> fusedAPIImpl.getOpenSourceApps(category)
            Source.PWA -> fusedAPIImpl.getPWAApps(category)
            else -> fusedAPIImpl.getGplayAppsByCategory(authData, category, pageUrl)
        }
    }

    fun getFusedAppInstallationStatus(fusedApp: FusedApp): Status {
        return fusedAPIImpl.getFusedAppInstallationStatus(fusedApp)
    }

    fun isAnyFusedAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ) = fusedAPIImpl.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)

    fun isAnyAppInstallStatusChanged(currentList: List<FusedApp>) =
        fusedAPIImpl.isAnyAppInstallStatusChanged(currentList)

    fun isOpenSourceSelected() = fusedAPIImpl.isOpenSourceSelected()
}
