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

package foundation.e.apps.data.cleanapk

import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.cleanapk.data.home.HomeScreen
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CleanApkRetrofit {

    companion object {
        // API endpoints
        const val BASE_URL = "https://api.dev.cleanapk.org/v2/"
        const val ASSET_URL = "https://api.dev.cleanapk.org/v2/media/"

        // Application sources
        const val APP_SOURCE_FOSS = "open"
        const val APP_SOURCE_ANY = "any"

        // Application types
        const val APP_TYPE_NATIVE = "native"
        const val APP_TYPE_PWA = "pwa"
        const val APP_TYPE_ANY = "any"
    }

    @GET("apps?action=list_home")
    suspend fun getHomeScreenData(
        @Query("type") type: String = APP_TYPE_ANY,
        @Query("source") source: String = APP_SOURCE_ANY,
    ): Response<HomeScreen>

    // TODO: Reminder that this function is for search App and PWA both
    @GET("apps?action=app_detail")
    suspend fun getAppOrPWADetailsByID(
        @Query("id") id: String,
        @Query("architectures") architectures: List<String>? = null,
        @Query("type") type: String? = null
    ): Response<Application>

    @GET("apps?action=search")
    suspend fun searchApps(
        @Query("keyword") keyword: String,
        @Query("source") source: String = APP_SOURCE_FOSS,
        @Query("type") type: String = APP_TYPE_ANY,
        @Query("nres") nres: Int = 20,
        @Query("page") page: Int = 1,
        @Query("by") by: String? = null,
    ): Response<Search>

    @GET("apps?action=list_apps")
    suspend fun listApps(
        @Query("category") category: String,
        @Query("source") source: String = APP_SOURCE_FOSS,
        @Query("type") type: String = APP_TYPE_ANY,
        @Query("nres") nres: Int = 20,
        @Query("page") page: Int = 1,
    ): Response<Search>

    @GET("apps?action=list_apps")
    suspend fun checkAvailablePackages(
        @Query("package_names[]") packages: List<String>,
        @Query("source") source: String = "open",
    ): Response<Search>

    @GET("apps?action=download")
    suspend fun getDownloadInfo(
        @Query("app_id") id: String,
        @Query("version") version: String? = null,
        @Query("architecture") architecture: String? = null
    ): Response<Download>

    @GET("apps?action=list_cat")
    suspend fun getCategoriesList(
        @Query("type") type: String = APP_TYPE_ANY,
        @Query("source") source: String = APP_SOURCE_ANY,
    ): Response<Categories>
}
