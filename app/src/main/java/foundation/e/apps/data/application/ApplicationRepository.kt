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

package foundation.e.apps.data.application

import androidx.lifecycle.LiveData
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.category.CategoryApi
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.downloadInfo.DownloadInfoApi
import foundation.e.apps.data.application.home.HomeApi
import foundation.e.apps.data.application.search.GplaySearchResult
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.ui.search.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationRepository @Inject constructor(
    private val searchAPIImpl: SearchApi,
    private val homeApi: HomeApi,
    private val categoryApi: CategoryApi,
    private val appsApi: AppsApi,
    private val downloadInfoApi: DownloadInfoApi
) {

    suspend fun getHomeScreenData(authData: AuthData): LiveData<ResultSupreme<List<Home>>> {
        return homeApi.fetchHomeScreenData(authData)
    }

    fun getSelectedAppTypes(): List<String> {
        return searchAPIImpl.getSelectedAppTypes()
    }

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<Application>, ResultStatus> {
        return appsApi.getApplicationDetails(packageNameList, authData, origin)
    }

    suspend fun getAppFilterLevel(application: Application, authData: AuthData?): FilterLevel {
        return appsApi.getAppFilterLevel(application, authData)
    }

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<Application, ResultStatus> {
        return appsApi.getApplicationDetails(id, packageName, authData, origin)
    }

    suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus> {
        return appsApi.getCleanapkAppDetails(packageName)
    }

    suspend fun updateFusedDownloadWithDownloadingInfo(
        origin: Origin,
        fusedDownload: FusedDownload
    ) {
        downloadInfoApi.updateFusedDownloadWithDownloadingInfo(
            origin,
            fusedDownload
        )
    }

    suspend fun getOSSDownloadInfo(id: String, version: String? = null) =
        downloadInfoApi.getOSSDownloadInfo(id, version)

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String? {
        return downloadInfoApi.getOnDemandModule(packageName, moduleName, versionCode, offerType)
    }

    suspend fun getCategoriesList(
        type: CategoryType,
    ): Pair<List<Category>, ResultStatus> {
        return categoryApi.getCategoriesList(type)
    }

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        return searchAPIImpl.getSearchSuggestions(query)
    }

    suspend fun getCleanApkSearchResults(
        query: String,
        authData: AuthData
    ): SearchResult {
        return searchAPIImpl.getCleanApkSearchResults(query, authData)
    }

    suspend fun getGplaySearchResults(
        query: String,
        nextPageSubBundle: Set<SearchBundle.SubBundle>?
    ): GplaySearchResult {
        return searchAPIImpl.getGplaySearchResult(query, nextPageSubBundle)
    }

    suspend fun getAppsListBasedOnCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?,
        source: Source
    ): ResultSupreme<Pair<List<Application>, String>> {
        return when (source) {
            Source.OPEN -> categoryApi.getCleanApkAppsByCategory(category, Source.OPEN)
            Source.PWA -> categoryApi.getCleanApkAppsByCategory(category, Source.PWA)
            else -> categoryApi.getGplayAppsByCategory(authData, category, pageUrl)
        }
    }

    fun getFusedAppInstallationStatus(application: Application): Status {
        return appsApi.getFusedAppInstallationStatus(application)
    }

    fun isAnyFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ) = appsApi.isAnyFusedAppUpdated(newApplications, oldApplications)

    fun isAnyAppInstallStatusChanged(currentList: List<Application>) =
        appsApi.isAnyAppInstallStatusChanged(currentList)

    fun isOpenSourceSelected() = appsApi.isOpenSourceSelected()
}
