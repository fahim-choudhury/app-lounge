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

package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.cleanapk.data.app.Application
import foundation.e.apps.api.cleanapk.data.categories.Categories
import foundation.e.apps.api.cleanapk.data.download.Download
import foundation.e.apps.api.cleanapk.data.home.HomeScreen
import foundation.e.apps.api.cleanapk.data.search.Search
import retrofit2.Response
import javax.inject.Inject

class CleanAPKRepository @Inject constructor(
    private val cleanAPKInterface: CleanAPKInterface,
    private val cleanApkAppDetailApi: CleanApkAppDetailApi
) {

    suspend fun getHomeScreenData(
        type: String = CleanAPKInterface.APP_TYPE_ANY,
        source: String = CleanAPKInterface.APP_SOURCE_ANY
    ): Response<HomeScreen> {
        return cleanAPKInterface.getHomeScreenData(type, source)
    }

    suspend fun getAppOrPWADetailsByID(
        id: String,
        architectures: List<String>? = null,
        type: String? = null
    ): Response<Application> {
        return cleanApkAppDetailApi.getAppOrPWADetailsByID(id, architectures, type)
    }

    suspend fun searchApps(
        keyword: String,
        source: String = CleanAPKInterface.APP_SOURCE_FOSS,
        type: String = CleanAPKInterface.APP_TYPE_ANY,
        nres: Int = 20,
        page: Int = 1,
        by: String? = null
    ): Response<Search> {
        return cleanAPKInterface.searchApps(keyword, source, type, nres, page, by)
    }

    suspend fun listApps(
        category: String,
        source: String = CleanAPKInterface.APP_SOURCE_FOSS,
        type: String = CleanAPKInterface.APP_TYPE_ANY,
        nres: Int = 20,
        page: Int = 1,
    ): Response<Search> {
        return cleanAPKInterface.listApps(category, source, type, nres, page)
    }

    suspend fun getDownloadInfo(
        id: String,
        version: String? = null,
        architecture: String? = null
    ): Response<Download> {
        return cleanAPKInterface.getDownloadInfo(id, version, architecture)
    }

    suspend fun getCategoriesList(
        type: String = CleanAPKInterface.APP_TYPE_ANY,
        source: String = CleanAPKInterface.APP_SOURCE_ANY
    ): Response<Categories> {
        return cleanAPKInterface.getCategoriesList(type, source)
    }
}
