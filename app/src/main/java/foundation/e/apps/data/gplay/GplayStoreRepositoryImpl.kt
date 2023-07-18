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

package foundation.e.apps.data.gplay

import android.content.Context
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.CategoryAppsHelper
import com.aurora.gplayapi.helpers.CategoryHelper
import com.aurora.gplayapi.helpers.Chart
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.fused.utils.CategoryType
import foundation.e.apps.data.gplay.utils.GPlayHttpClient
import foundation.e.apps.data.login.LoginSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GplayStoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val loginSourceRepository: LoginSourceRepository
) : GplayStoreRepository {

    override suspend fun getHomeScreenData(): Any {
        val homeScreenData = mutableMapOf<String, List<App>>()
        val homeElements = createTopChartElements()
        val authData = loginSourceRepository.gplayAuth ?: return homeScreenData

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

    override suspend fun getSearchResult(
        query: String,
    ): Flow<Pair<List<App>, Boolean>> {
        return flow {

            /*
             * Variable names and logic made same as that of Aurora store.
             * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
             */
            var authData = loginSourceRepository.gplayAuth ?: return@flow

            val searchHelper =
                SearchHelper(authData).using(gPlayHttpClient)
            val searchBundle = searchHelper.searchResults(query)

            val initialReplacedList = mutableListOf<App>()
            val INITIAL_LIMIT = 4

            emitReplacedList(
                this@flow,
                initialReplacedList,
                INITIAL_LIMIT,
                searchBundle,
                true,
            )

            var nextSubBundleSet: MutableSet<SearchBundle.SubBundle>
            do {
                nextSubBundleSet = fetchNextSubBundle(
                    searchBundle,
                    searchHelper,
                    this@flow,
                    initialReplacedList,
                    INITIAL_LIMIT
                )
            } while (nextSubBundleSet.isNotEmpty())

            /*
             * If initialReplacedList size is less than INITIAL_LIMIT,
             * it means the results were very less and nothing has been emitted so far.
             * Hence emit the list.
             */
            if (initialReplacedList.size < INITIAL_LIMIT) {
                emitInMain(this@flow, initialReplacedList, false)
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun fetchNextSubBundle(
        searchBundle: SearchBundle,
        searchHelper: SearchHelper,
        scope: FlowCollector<Pair<List<App>, Boolean>>,
        accumulationList: MutableList<App>,
        accumulationLimit: Int,
    ): MutableSet<SearchBundle.SubBundle> {
        val nextSubBundleSet = searchBundle.subBundles
        val newSearchBundle = searchHelper.next(nextSubBundleSet)
        if (newSearchBundle.appList.isNotEmpty()) {
            searchBundle.apply {
                subBundles.clear()
                subBundles.addAll(newSearchBundle.subBundles)
                emitReplacedList(
                    scope,
                    accumulationList,
                    accumulationLimit,
                    newSearchBundle,
                    nextSubBundleSet.isNotEmpty(),
                )
            }
        }
        return nextSubBundleSet
    }

    override suspend fun getSearchSuggestions(query: String): List<SearchSuggestEntry> {
        val authData = loginSourceRepository.gplayAuth ?: return listOf()

        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
    }

    override suspend fun getAppsByCategory(category: String, pageUrl: String?): StreamCluster {
        val authData = loginSourceRepository.gplayAuth ?: return StreamCluster()

        val subCategoryHelper =
            CategoryAppsHelper(authData).using(gPlayHttpClient)

        if (!pageUrl.isNullOrEmpty()) {
            return subCategoryHelper.next(pageUrl)
        }

        return subCategoryHelper.getCategoryAppsList(category.uppercase())
    }

    override suspend fun getCategories(type: CategoryType?): List<Category> {
        val categoryList = mutableListOf<Category>()
        if (type == null) {
            return categoryList
        }

        val authData = loginSourceRepository.gplayAuth ?: return categoryList

        withContext(Dispatchers.IO) {
            val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)
            categoryList.addAll(categoryHelper.getAllCategoriesList(getCategoryType(type)))
        }
        return categoryList
    }

    override suspend fun getAppDetails(packageNameOrId: String): App? {
        var appDetails: App?
        val authData = loginSourceRepository.gplayAuth ?: return null

        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetails = appDetailsHelper.getAppByPackageName(packageNameOrId)
        }
        return appDetails
    }

    override suspend fun getAppsDetails(packageNamesOrIds: List<String>): List<App> {
        val appDetailsList = mutableListOf<App>()
        val authData = loginSourceRepository.gplayAuth ?: return appDetailsList

        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetailsList.addAll(appDetailsHelper.getAppByPackageName(packageNamesOrIds))
        }
        return appDetailsList
    }

    private fun getCategoryType(type: CategoryType): Category.Type {
        return if (type == CategoryType.APPLICATION) Category.Type.APPLICATION else Category.Type.GAME
    }

    private suspend fun emitReplacedList(
        scope: FlowCollector<Pair<List<App>, Boolean>>,
        accumulationList: MutableList<App>,
        accumulationLimit: Int,
        searchBundle: SearchBundle,
        moreToEmit: Boolean,
    ) {
        searchBundle.appList.forEach {
            when {
                accumulationList.size < accumulationLimit - 1 -> {
                    /*
                     * If initial limit is 4, add apps to list (without emitting)
                     * till 2 apps.
                     */
                    accumulationList.add(it)
                }

                accumulationList.size == accumulationLimit - 1 -> {
                    /*
                     * If initial limit is 4, and we have reached till 3 apps,
                     * add the 4th app and emit the list.
                     */
                    accumulationList.add(it)
                    scope.emit(Pair(accumulationList, moreToEmit))
                    emitInMain(scope, accumulationList, moreToEmit)
                }

                accumulationList.size == accumulationLimit -> {
                    /*
                     * If initial limit is 4, and we have emitted 4 apps,
                     * for all rest of the apps, emit each app one by one.
                     */
                    emitInMain(scope, listOf(it), moreToEmit)
                }
            }
        }
    }

    private suspend fun emitInMain(
        scope: FlowCollector<Pair<List<App>, Boolean>>,
        it: List<App>,
        moreToEmit: Boolean
    ) {
        scope.emit(Pair(it, moreToEmit))
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

    override suspend fun getDownloadInfo(
        idOrPackageName: String,
        versionCode: Any?,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
        val authData = loginSourceRepository.gplayAuth ?: return downloadData

        withContext(Dispatchers.IO) {
            val version = versionCode?.let { it as Int } ?: -1
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(purchaseHelper.purchase(idOrPackageName, version, offerType))
        }
        return downloadData
    }

    override suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
        val authData = loginSourceRepository.gplayAuth ?: return downloadData

        withContext(Dispatchers.IO) {
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(
                purchaseHelper.getOnDemandModule(
                    packageName,
                    moduleName,
                    versionCode,
                    offerType
                )
            )
        }
        return downloadData
    }
}
