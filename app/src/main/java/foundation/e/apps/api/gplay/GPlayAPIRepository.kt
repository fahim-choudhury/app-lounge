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

package foundation.e.apps.api.gplay

import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.TopChartsHelper
import javax.inject.Inject

class GPlayAPIRepository @Inject constructor(
    private val gPlayAPIImpl: GPlayAPIImpl
) {

    suspend fun fetchAuthData(): Boolean {
        return gPlayAPIImpl.fetchAuthData()
    }

    suspend fun fetchAuthData(email: String, aasToken: String): AuthData {
        return gPlayAPIImpl.fetchAuthData(email, aasToken)
    }

    suspend fun validateAuthData(authData: AuthData): Boolean {
        return gPlayAPIImpl.validateAuthData(authData)
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        return gPlayAPIImpl.getSearchSuggestions(query, authData)
    }

    suspend fun getSearchResults(query: String, authData: AuthData): List<App> {
        return gPlayAPIImpl.getSearchResults(query, authData)
    }

    suspend fun getDownloadInfo(
        packageName: String,
        versionCode: Int,
        offerType: Int,
        authData: AuthData
    ): List<File> {
        return gPlayAPIImpl.getDownloadInfo(packageName, versionCode, offerType, authData)
    }

    suspend fun getAppDetails(packageName: String, authData: AuthData): App? {
        return gPlayAPIImpl.getAppDetails(packageName, authData)
    }

    suspend fun getAppDetails(packageNameList: List<String>, authData: AuthData): List<App> {
        return gPlayAPIImpl.getAppDetails(packageNameList, authData)
    }

    suspend fun getTopApps(
        type: TopChartsHelper.Type,
        chart: TopChartsHelper.Chart,
        authData: AuthData
    ): List<App> {
        return gPlayAPIImpl.getTopApps(type, chart, authData)
    }

    suspend fun getCategoriesList(type: Category.Type, authData: AuthData): List<Category> {
        return gPlayAPIImpl.getCategoriesList(type, authData)
    }

    suspend fun getNextStreamBundle(
        authData: AuthData,
        homeUrl: String,
        currentStreamBundle: StreamBundle,
    ): StreamBundle {
        return gPlayAPIImpl.getNextStreamBundle(authData, homeUrl, currentStreamBundle)
    }

    suspend fun getAdjustedFirstCluster(
        authData: AuthData,
        streamBundle: StreamBundle,
        pointer: Int = 0,
    ): StreamCluster {
        return gPlayAPIImpl.getAdjustedFirstCluster(authData, streamBundle, pointer)
    }

    suspend fun getNextStreamCluster(
        authData: AuthData,
        currentStreamCluster: StreamCluster,
    ): StreamCluster {
        return gPlayAPIImpl.getNextStreamCluster(authData, currentStreamCluster)
    }

    suspend fun listApps(browseUrl: String, authData: AuthData): List<App> {
        return gPlayAPIImpl.listApps(browseUrl, authData)
    }
}
