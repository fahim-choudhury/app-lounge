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
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.CategoryAppsHelper
import com.aurora.gplayapi.helpers.CategoryHelper
import com.aurora.gplayapi.helpers.Chart
import com.aurora.gplayapi.helpers.ExpandedBrowseHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.StreamHelper
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class GplayStoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val loginSourceRepository: LoginSourceRepository
) : GplayStoreRepository {

    private val authData by lazy { loginSourceRepository.gplayAuth!! }

    override suspend fun getHomeScreenData(): Any {
        val homeScreenData = mutableMapOf<String, List<App>>()
        val homeElements = createTopChartElements()

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
        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
    }

    override suspend fun getAppsByCategory(category: String, paginationParameter: Any?): StreamCluster {
        val subCategoryHelper =
            CategoryAppsHelper(authData).using(gPlayHttpClient)

        paginationParameter?.let {
            if (it is String && it.isNotEmpty()) {
                return subCategoryHelper.next(it)
            }
        }

        return subCategoryHelper.getCategoryAppsList(category.uppercase())
    }

    override suspend fun getCategories(type: CategoryType?): List<Category> {
        val categoryList = mutableListOf<Category>()
        if (type == null) {
            return categoryList
        }

        withContext(Dispatchers.IO) {
            val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)
            categoryList.addAll(categoryHelper.getAllCategoriesList(getCategoryType(type)))
        }
        return categoryList
    }

    override suspend fun getAppDetails(packageNameOrId: String): App? {
        var appDetails: App?
        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetails = appDetailsHelper.getAppByPackageName(packageNameOrId)
        }
        return appDetails
    }

    override suspend fun getAppsDetails(packageNamesOrIds: List<String>): List<App> {
        val appDetailsList = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetailsList.addAll(appDetailsHelper.getAppByPackageName(packageNamesOrIds))
        }
        return appDetailsList
    }

    private fun getCategoryType(type: CategoryType): Category.Type {
        return if (type == CategoryType.APPLICATION) Category.Type.APPLICATION else Category.Type.GAME
    }

    private suspend fun getGplayApps(category: String): List<App> {
        val list = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            supervisorScope {
                val categoryHelper =
                    CategoryHelper(authData).using(gPlayHttpClient)

                var streamBundle: StreamBundle
                var nextStreamBundleUrl = category

                /*
                 * Logic: We start with the browseUrl.
                 * When we call getSubCategoryBundle(), we get a new StreamBundle object, having
                 * StreamClusters, which have app data.
                 * The generated StreamBundle also has a url for next StreamBundle to be generated
                 * with fresh app data.
                 * Hence we loop as long as the StreamBundle's next page url is not blank.
                 */
                do {
                    streamBundle = categoryHelper.getSubCategoryBundle(nextStreamBundleUrl)
                    val streamClusters = streamBundle.streamClusters

                    /*
                     * Similarly to the logic of StreamBundles, each StreamCluster can have a url,
                     * pointing to another StreamCluster with new set of app data.
                     * We loop over all the StreamCluster of one StreamBundle, and for each of the
                     * StreamCluster we continue looping as long as the StreamCluster.clusterNextPageUrl
                     * is not blank.
                     */
                    streamClusters.values.forEach { streamCluster ->
                        list.addAll(streamCluster.clusterAppList) // Add all apps for this StreamCluster

                        // Loop over possible next StreamClusters
                        var currentStreamCluster = streamCluster
                        while (currentStreamCluster.hasNext()) {
                            currentStreamCluster = categoryHelper
                                .getNextStreamCluster(currentStreamCluster.clusterNextPageUrl)
                                .also {
                                    list.addAll(it.clusterAppList)
                                }
                        }
                    }

                    nextStreamBundleUrl = streamBundle.streamNextPageUrl
                } while (streamBundle.hasNext())
            }
        }
        return list.distinctBy { it.packageName }
    }

    /*
     * Get next StreamCluster from currentNextPageUrl.
     * This method is to be called when the scrollview reaches the bottom.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private suspend fun getNextStreamCluster(
        authData: AuthData,
        currentStreamCluster: StreamCluster,
    ): StreamCluster {
        return withContext(Dispatchers.IO) {
            if (!currentStreamCluster.hasNext()) {
                return@withContext StreamCluster()
            }

            /*
             * Logic found in Aurora store code.
             */
            return@withContext if (currentStreamCluster.clusterNextPageUrl.contains("expanded")) {
                getExpandedStreamCluster(
                    currentStreamCluster.clusterNextPageUrl,
                    authData
                )
            } else {
                getStreamCluster(currentStreamCluster.clusterNextPageUrl, authData)
            }
        }
    }

    private fun getExpandedStreamCluster(
        url: String,
        authData: AuthData,
        checkStartUrl: Boolean = false
    ): StreamCluster {
        ExpandedBrowseHelper(authData).using(gPlayHttpClient).run {

            if (!checkStartUrl) {
                return getExpandedBrowseClusters(url)
            }

            val browseResponse = getBrowseStreamResponse(url)

            if (browseResponse.hasBrowseTab()) {
                return getExpandedBrowseClusters(browseResponse.browseTab.listUrl)
            }
        }
        return StreamCluster()
    }

    private fun getStreamCluster(
        url: String,
        authData: AuthData,
        checkStartUrl: Boolean = false
    ): StreamCluster {
        StreamHelper(authData).using(gPlayHttpClient).run {

            if (!checkStartUrl) {
                return getNextStreamCluster(url)
            }

            val browseResponse = getBrowseStreamResponse(url)

            if (browseResponse.contentsUrl.isNotEmpty()) {
                return getNextStreamCluster(browseResponse.contentsUrl)
            }

            if (browseResponse.hasBrowseTab()) {
                return getNextStreamCluster(browseResponse.browseTab.listUrl)
            }
        }
        return StreamCluster()
    }

    suspend fun getNextStreamBundle(
        authData: AuthData,
        homeUrl: String,
        currentStreamBundle: StreamBundle,
    ): StreamBundle {
        return withContext(Dispatchers.IO) {
            val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)
            if (currentStreamBundle.streamClusters.isEmpty()) {
                categoryHelper.getSubCategoryBundle(homeUrl)
            } else {
                categoryHelper.getSubCategoryBundle(currentStreamBundle.streamNextPageUrl)
            }
        }
    }

    /**
     * Get first adjusted StreamCluster of a StreamBundle.
     *
     * Takes the clusterBrowseUrl of streamBundle.streamClusters[[pointer]],
     * Populates the cluster and returns it.
     *
     * This does not always operate on zeroth StreamCluster of [streamBundle].
     * A StreamBundle can have many StreamClusters, each of the individual StreamCluster can point
     * to completely different StreamClusters.
     *
     * StreamBundle 1 (streamNextPageUrl points to StreamBundle 2)
     *    StreamCluster 1 -> StreamCluster 1.1 -> StreamCluster 1.2 ....
     *    StreamCluster 2 -> StreamCluster 2.1 -> StreamCluster 2.2 ....
     *    StreamCluster 3 -> StreamCluster 3.1 -> StreamCluster 3.2 ....
     *
     * Here [pointer] refers to the position of StreamCluster 1, 2, 3.... but not 1.1, 2.1 ....
     * The subsequent clusters (1.1, 1.2, .. 2.1 ..) are accessed by [getNextStreamCluster].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    suspend fun getAdjustedFirstCluster(
        authData: AuthData,
        streamBundle: StreamBundle,
        pointer: Int = 0,
    ): StreamCluster {
        return withContext(Dispatchers.IO) {
            val clusterSize = streamBundle.streamClusters.size
            if (clusterSize != 0 && pointer < clusterSize && pointer >= 0) {
                val firstCluster = streamBundle.streamClusters.values.toList()[pointer]

                val clusterBrowseUrl = firstCluster.clusterBrowseUrl

                /*
                 * Logic found in Aurora store code.
                 */
                val adjustedCluster = if (firstCluster.clusterBrowseUrl.contains("expanded")) {
                    getExpandedStreamCluster(clusterBrowseUrl, authData, true)
                } else {
                    getStreamCluster(clusterBrowseUrl, authData, true)
                }

                return@withContext adjustedCluster.apply {
                    clusterAppList.addAll(firstCluster.clusterAppList)
                    if (!hasNext()) {
                        clusterNextPageUrl = firstCluster.clusterNextPageUrl
                    }
                }
            }

            /*
             * If nothing works return blank StreamCluster.
             */
            StreamCluster()
        }
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
            val cluster = topChartsHelper.getCluster(type, chart)
            Timber.d("Next cluster url: ${cluster.clusterNextPageUrl}")
            topApps.addAll(cluster.clusterAppList)
        }
        return topApps
    }

    override suspend fun getDownloadInfo(
        idOrPackageName: String,
        versionCode: Any?,
        offerType: Int
    ): List<File> {
        val downloadData = mutableListOf<File>()
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
