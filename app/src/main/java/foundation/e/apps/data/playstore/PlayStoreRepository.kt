/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.playstore

import android.content.Context
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.CategoryAppsHelper
import com.aurora.gplayapi.helpers.CategoryHelper
import com.aurora.gplayapi.helpers.Chart
import com.aurora.gplayapi.helpers.ContentRatingHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.StoreRepository
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class PlayStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val authenticatorRepository: AuthenticatorRepository
) : StoreRepository {

    override suspend fun getHomeScreenData(): Any {
        val homeScreenData = mutableMapOf<String, List<App>>()
        val homeElements = createTopChartElements()
        val authData = authenticatorRepository.gplayAuth!!

        homeElements.forEach {
            val chart = it.value.keys.iterator().next()
            val type = it.value.values.iterator().next()
            val result = getTopApps(type, chart, authData)
            homeScreenData[it.key] = result
        }

        return homeScreenData
    }

    private fun createTopChartElements() = mutableMapOf(
        context.getString(R.string.topselling_free_apps) to mapOf(Chart.TOP_SELLING_FREE to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.topselling_free_games) to mapOf(Chart.TOP_SELLING_FREE to TopChartsHelper.Type.GAME),
        context.getString(R.string.topgrossing_apps) to mapOf(Chart.TOP_GROSSING to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.topgrossing_games) to mapOf(Chart.TOP_GROSSING to TopChartsHelper.Type.GAME),
        context.getString(R.string.movers_shakers_apps) to mapOf(Chart.MOVERS_SHAKERS to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.movers_shakers_games) to mapOf(Chart.MOVERS_SHAKERS to TopChartsHelper.Type.GAME),
    )

    suspend fun getSearchResult(
        query: String,
        subBundle: MutableSet<SearchBundle.SubBundle>?
    ): Pair<List<App>, MutableSet<SearchBundle.SubBundle>> {
        val authData = authenticatorRepository.gplayAuth!!
        val searchHelper = SearchHelper(authData).using(gPlayHttpClient)

        Timber.d("Fetching search result for $query, subBundle: $subBundle")

        subBundle?.let {
            val searchResult = searchHelper.next(it)
            Timber.d("fetching next page search data...")
            return getSearchResultPair(searchResult, query)
        }

        val searchResult = searchHelper.searchResults(query)
        return getSearchResultPair(searchResult, query)
    }

    private fun getSearchResultPair(
        searchBundle: SearchBundle,
        query: String
    ): Pair<MutableList<App>, MutableSet<SearchBundle.SubBundle>> {
        val apps = searchBundle.appList
        Timber.d("Found ${apps.size} apps for query, $query")
        return Pair(apps, searchBundle.subBundles)
    }

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        val authData = authenticatorRepository.gplayAuth!!

        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
    }

    suspend fun getAppsByCategory(category: String, pageUrl: String?): StreamCluster {
        val authData = authenticatorRepository.gplayAuth!!

        val subCategoryHelper = CategoryAppsHelper(authData).using(gPlayHttpClient)

        if (!pageUrl.isNullOrEmpty()) {
            return subCategoryHelper.next(pageUrl)
        }

        return subCategoryHelper.getCategoryAppsList(category.uppercase())
    }

    suspend fun getCategories(type: CategoryType?): List<Category> {
        val categoryList = mutableListOf<Category>()
        if (type == null) {
            return categoryList
        }

        val authData = authenticatorRepository.gplayAuth!!

        withContext(Dispatchers.IO) {
            val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)
            categoryList.addAll(categoryHelper.getAllCategoriesList(getCategoryType(type)))
        }
        return categoryList
    }

    override suspend fun getAppDetails(packageNameOrId: String): App? {
        var appDetails: App?
        val authData = authenticatorRepository.gplayAuth!!

        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetails = appDetailsHelper.getAppByPackageName(packageNameOrId)
        }
        return appDetails
    }

    suspend fun getAppsDetails(packageNamesOrIds: List<String>): List<App> {
        val appDetailsList = mutableListOf<App>()
        val authData = authenticatorRepository.gplayAuth!!

        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetailsList.addAll(appDetailsHelper.getAppByPackageName(packageNamesOrIds))
        }
        return appDetailsList
    }

    private fun getCategoryType(type: CategoryType): Category.Type {
        return if (type == CategoryType.APPLICATION) Category.Type.APPLICATION
        else Category.Type.GAME
    }

    private suspend fun getTopApps(
        type: TopChartsHelper.Type,
        chart: Chart,
        authData: AuthData
    ): List<App> {
        val topApps = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            val topChartsHelper = TopChartsHelper(authData).using(gPlayHttpClient)
            topApps.addAll(topChartsHelper.getCluster(type, chart).clusterAppList)
        }
        return topApps
    }

    suspend fun getDownloadInfo(
        idOrPackageName: String,
        versionCode: Any?,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
        val authData = authenticatorRepository.gplayAuth!!

        withContext(Dispatchers.IO) {
            val version = versionCode?.let { it as Int } ?: -1
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(purchaseHelper.purchase(idOrPackageName, version, offerType))
        }
        return downloadData
    }

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
        val authData = authenticatorRepository.gplayAuth!!

        withContext(Dispatchers.IO) {
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(
                purchaseHelper.getOnDemandModule(packageName, moduleName, versionCode, offerType)
            )
        }
        return downloadData
    }

    suspend fun getContentRatingWithId(
        appPackage: String,
        contentRating: ContentRating
    ): ContentRating {
        val authData = authenticatorRepository.gplayAuth!!
        val contentRatingHelper = ContentRatingHelper(authData)

        return withContext(Dispatchers.IO) {
            contentRatingHelper.updateContentRatingWithId(
                appPackage,
                contentRating
            )
        }
    }

    suspend fun getEnglishContentRating(packageName: String): ContentRating? {
        val authData = authenticatorRepository.gplayAuth ?: return null
        val contentRatingHelper = ContentRatingHelper(authData)

        return withContext(Dispatchers.IO) {
            contentRatingHelper.getEnglishContentRating(packageName)
        }
    }
}
