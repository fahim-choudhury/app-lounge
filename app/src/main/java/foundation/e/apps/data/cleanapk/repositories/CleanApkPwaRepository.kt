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

package foundation.e.apps.data.cleanapk.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.data.search.Search
import retrofit2.Response
import javax.inject.Inject

class CleanApkPwaRepository @Inject constructor(
    private val cleanApkRetrofit: CleanApkRetrofit,
    private val homeConverter: HomeConverter,
    @ApplicationContext val context: Context
) : CleanApkRepository {

    override suspend fun getHomeScreenData(): Map<String, List<Application>> {
        val response =  cleanApkRetrofit.getHomeScreenData(
            CleanApkRetrofit.APP_TYPE_PWA,
            CleanApkRetrofit.APP_SOURCE_ANY
        )

        val home = response.body()?.home ?: throw IllegalStateException("No home data found")

        val listHome = homeConverter.toGenericHome(home, CleanApkRetrofit.APP_TYPE_PWA)
        val map = mutableMapOf<String, List<Application>>()
        listHome.forEach {
            map[it.title] = it.list
        }

        return map
    }

    override suspend fun getSearchResult(query: String, searchBy: String?): Response<Search> {
        return cleanApkRetrofit.searchApps(
            query,
            CleanApkRetrofit.APP_SOURCE_ANY,
            CleanApkRetrofit.APP_TYPE_PWA,
            20,
            1,
            searchBy
        )
    }

    override suspend fun getAppsByCategory(category: String, paginationParameter: Any?): Response<Search> {
        return cleanApkRetrofit.listApps(
            category,
            CleanApkRetrofit.APP_SOURCE_ANY,
            CleanApkRetrofit.APP_TYPE_PWA,
            NUMBER_OF_ITEMS,
            NUMBER_OF_PAGES
        )
    }

    override suspend fun getCategories(): Response<Categories> {
        return cleanApkRetrofit.getCategoriesList(
            CleanApkRetrofit.APP_TYPE_PWA,
            CleanApkRetrofit.APP_SOURCE_ANY
        )
    }

    override suspend fun checkAvailablePackages(packageNames: List<String>): Response<Search> {
        return cleanApkRetrofit.checkAvailablePackages(packageNames)
    }

    override suspend fun getAppDetails(packageNameOrId: String): Application {
        val response = cleanApkRetrofit.getAppOrPWADetailsByID(packageNameOrId, null, null)
        return response.body()?.app ?: throw IllegalStateException("No app data found")
    }
}
