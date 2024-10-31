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
import com.aurora.gplayapi.data.models.App as GplayApp
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.ContentRatingHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.contracts.TopChartsContract.Chart
import com.aurora.gplayapi.helpers.contracts.TopChartsContract.Type
import com.aurora.gplayapi.helpers.web.WebCategoryHelper
import com.aurora.gplayapi.helpers.web.WebCategoryStreamHelper
import com.aurora.gplayapi.helpers.web.WebSearchHelper
import com.aurora.gplayapi.helpers.web.WebTopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.StoreRepository
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class PlayStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val authenticatorRepository: AuthenticatorRepository,
    private val applicationDataManager: ApplicationDataManager
) : StoreRepository {

    override suspend fun getHomeScreenData(list: MutableList<Home>): List<Home> {
        val homeScreenData = mutableMapOf<String, List<Application>>()
        val homeElements = createTopChartElements()

        homeElements.forEach {
            if (it.value.isEmpty()) return@forEach

            val chart = it.value.keys.iterator().next()
            val type = it.value.values.iterator().next()
            val result = getTopApps(type, chart)
            homeScreenData[it.key] = result
        }

        homeScreenData.map {
            val fusedApps = it.value.map { app ->
                app.apply {
                    applicationDataManager.updateStatus(this)
                    applicationDataManager.updateFilterLevel(this)
                }
            }
            list.add(Home(it.key, fusedApps))
        }

        return list
    }

    private fun createTopChartElements() = mutableMapOf(
        context.getString(R.string.topselling_free_apps) to mapOf(Chart.TOP_SELLING_FREE to Type.APPLICATION),
        context.getString(R.string.topselling_free_games) to mapOf(Chart.TOP_SELLING_FREE to Type.GAME),
        context.getString(R.string.topgrossing_apps) to mapOf(Chart.TOP_GROSSING to Type.APPLICATION),
        context.getString(R.string.topgrossing_games) to mapOf(Chart.TOP_GROSSING to Type.GAME),
        context.getString(R.string.movers_shakers_apps) to mapOf(Chart.MOVERS_SHAKERS to Type.APPLICATION),
        context.getString(R.string.movers_shakers_games) to mapOf(Chart.MOVERS_SHAKERS to Type.GAME),
    )

    fun getSearchResult(
        query: String,
        subBundle: MutableSet<SearchBundle.SubBundle>?
    ): Pair<List<GplayApp>, MutableSet<SearchBundle.SubBundle>> {
        val searchHelper = WebSearchHelper().using(gPlayHttpClient)

        Timber.d("Fetching search result for $query, subBundle: $subBundle")

        val searchResult = if (subBundle != null) {
            Timber.d("fetching next page search data...")
            searchHelper.next(subBundle)
        } else {
            searchHelper.searchResults(query)
        }

        return getSearchResultPair(searchResult, query)
    }

    private fun getSearchResultPair(
        searchBundle: SearchBundle,
        query: String
    ): Pair<MutableList<GplayApp>, MutableSet<SearchBundle.SubBundle>> {
        val apps = searchBundle.appList
        Timber.d("Found ${apps.size} apps for query, $query")
        return Pair(apps, searchBundle.subBundles)
    }

    suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = WebSearchHelper().using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.title.isNotBlank() }
    }

    fun getAppsByCategory(category: String, pageUrl: String?): StreamCluster {
        val subCategoryHelper = WebCategoryStreamHelper().using(gPlayHttpClient)

        if (!pageUrl.isNullOrEmpty()) {
            return subCategoryHelper.nextStreamCluster(pageUrl)
        }

        val bundle = subCategoryHelper.fetch(upperCaseCategory(category))
        return bundle.streamClusters.entries.first().value
    }

    private fun upperCaseCategory(path: String): String {
        val lastPart = path.substringAfterLast("/").uppercase()
        val basePath = path.substringBeforeLast("/")
        return "$basePath/$lastPart"
     }

    suspend fun getCategories(type: CategoryType?): List<Category> {
        val categoryList = mutableListOf<Category>()
        if (type == null) {
            return categoryList
        }

        withContext(Dispatchers.IO) {
            val categoryHelper = WebCategoryHelper().using(gPlayHttpClient)
            categoryList.addAll(categoryHelper.getAllCategories(getCategoryType(type)))
        }
        return categoryList
    }

    override suspend fun getAppDetails(packageNameOrId: String): Application {
        var appDetails: GplayApp?

        val appDetailsHelper =
            AppDetailsHelper(authenticatorRepository.getGPlayAuthOrThrow()).using(gPlayHttpClient)

        withContext(Dispatchers.IO) {
            appDetails = appDetailsHelper.getAppByPackageName(packageNameOrId)
        }

        if (appDetails?.versionCode == 0) {
            throw IllegalStateException("App version code cannot be 0")
        }

        return appDetails?.toApplication(context) ?: Application()
    }

    suspend fun getAppsDetails(packageNamesOrIds: List<String>): List<GplayApp> {
        val appDetailsList = mutableListOf<GplayApp>()

        val appDetailsHelper =
            AppDetailsHelper(authenticatorRepository.getGPlayAuthOrThrow()).using(gPlayHttpClient)

        withContext(Dispatchers.IO) {
            appDetailsList.addAll(appDetailsHelper.getAppByPackageName(packageNamesOrIds))
        }

        if (appDetailsList.first().versionCode == 0) {
            throw IllegalStateException("App version code cannot be 0")
        }

        return appDetailsList
    }

    private fun getCategoryType(type: CategoryType): Category.Type {
        return if (type == CategoryType.APPLICATION) Category.Type.APPLICATION
        else Category.Type.GAME
    }

    private suspend fun getTopApps(
        type: Type,
        chart: Chart
    ): List<Application> {
        val topApps = mutableListOf<GplayApp>()
        withContext(Dispatchers.IO) {
            val topChartsHelper = WebTopChartsHelper().using(gPlayHttpClient)
            try {
                topApps.addAll(topChartsHelper.getCluster(type.value, chart.value).clusterAppList)
            } catch (exception: Exception) {
                Timber.w("Could not get top apps: $exception")
                topApps.addAll(emptyList())
            }
        }

        return topApps.map {
            it.toApplication(context)
        }
    }

    suspend fun getDownloadInfo(
        idOrPackageName: String,
        versionCode: Int,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
        val authData = authenticatorRepository.getGPlayAuthOrThrow()

        var version = versionCode
        var offer = offerType

        if (version == 0) {
            val appDetailsHelper = getAppDetails(idOrPackageName)
            version = appDetailsHelper.latest_version_code
            offer = appDetailsHelper.offer_type
        }

        if (version == 0) {
            throw IllegalStateException("Could not get download details for $idOrPackageName")
        }

        withContext(Dispatchers.IO) {
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(purchaseHelper.purchase(idOrPackageName, version, offer))
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
        val authData = authenticatorRepository.getGPlayAuthOrThrow()

        withContext(Dispatchers.IO) {
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(
                purchaseHelper.purchase(packageName, versionCode, offerType, moduleName)
            )
        }
        return downloadData
    }

    suspend fun getContentRatingWithId(
        appPackage: String,
        contentRating: ContentRating
    ): ContentRating {
        val authData = authenticatorRepository.getGPlayAuthOrThrow()
        val contentRatingHelper = ContentRatingHelper(authData)

        return withContext(Dispatchers.IO) {
            contentRatingHelper.updateContentRatingWithId(
                appPackage,
                contentRating
            )
        }
    }

    suspend fun getEnglishContentRating(packageName: String): ContentRating {
        val authData = authenticatorRepository.getGPlayAuthOrThrow()
        val contentRatingHelper = ContentRatingHelper(authData)

        return withContext(Dispatchers.IO) {
            contentRatingHelper.getEnglishContentRating(packageName)
        }
    }
}
