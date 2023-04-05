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

import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.liveData
import com.aurora.gplayapi.IntegrityAnswer
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.File
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.*
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GPlayAPIImpl @Inject constructor(private val gPlayHttpClient: GPlayHttpClient) {

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
    }

    /**
     * Sends livedata of list of apps being loaded from search and a boolean
     * signifying if more data is to be loaded.
     */
    fun getSearchResults(
        query: String,
        authData: AuthData,
        replaceWithFDroid: suspend (App) -> FusedApp,
    ): LiveData<Pair<List<FusedApp>, Boolean>> {
        /*
         * Send livedata to improve UI performance, so we don't have to wait for loading all results.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
         */
        return liveData {
            withContext(Dispatchers.IO) {
                /*
                 * Variable names and logic made same as that of Aurora store.
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
                 */
                val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
                val searchBundle = searchHelper.searchResults(query)

                val initialReplacedList = mutableListOf<FusedApp>()
                val INITIAL_LIMIT = 4

                emitReplacedList(
                    this@liveData,
                    initialReplacedList,
                    INITIAL_LIMIT,
                    replaceWithFDroid,
                    searchBundle,
                    true,
                )

                var nextSubBundleSet: MutableSet<SearchBundle.SubBundle>
                do {
                    nextSubBundleSet = searchBundle.subBundles
                    val newSearchBundle = searchHelper.next(nextSubBundleSet)
                    if (newSearchBundle.appList.isNotEmpty()) {
                        searchBundle.apply {
                            subBundles.clear()
                            subBundles.addAll(newSearchBundle.subBundles)
                            emitReplacedList(
                                this@liveData,
                                initialReplacedList,
                                INITIAL_LIMIT,
                                replaceWithFDroid,
                                newSearchBundle,
                                nextSubBundleSet.isNotEmpty(),
                            )
                        }
                    }
                } while (nextSubBundleSet.isNotEmpty())

                /*
                 * If initialReplacedList size is less than INITIAL_LIMIT,
                 * it means the results were very less and nothing has been emitted so far.
                 * Hence emit the list.
                 */
                if (initialReplacedList.size < INITIAL_LIMIT) {
                    emit(Pair(initialReplacedList, false))
                }
            }
        }
    }

    private suspend fun emitReplacedList(
        scope: LiveDataScope<Pair<List<FusedApp>, Boolean>>,
        accumulationList: MutableList<FusedApp>,
        accumulationLimit: Int,
        replaceFunction: suspend (App) -> FusedApp,
        searchBundle: SearchBundle,
        moreToEmit: Boolean,
    ) {
        searchBundle.appList.forEach {
            val replacedApp = replaceFunction(it)
            when {
                accumulationList.size < accumulationLimit - 1 -> {
                    /*
                     * If initial limit is 4, add apps to list (without emitting)
                     * till 2 apps.
                     */
                    accumulationList.add(replacedApp)
                }
                accumulationList.size == accumulationLimit - 1 -> {
                    /*
                     * If initial limit is 4, and we have reached till 3 apps,
                     * add the 4th app and emit the list.
                     */
                    accumulationList.add(replacedApp)
                    scope.emit(Pair(accumulationList, moreToEmit))
                }
                accumulationList.size == accumulationLimit -> {
                    /*
                     * If initial limit is 4, and we have emitted 4 apps,
                     * for all rest of the apps, emit each app one by one.
                     */
                    scope.emit(Pair(listOf(replacedApp), moreToEmit))
                }
            }
        }
    }

    suspend fun getDownloadInfo(
        packageName: String,
        versionCode: Int,
        offerType: Int,
        authData: AuthData
    ): List<File> {
        val downloadData = mutableListOf<File>()
        withContext(Dispatchers.IO) {
            val purchaseHelper = PurchaseHelper(authData).using(gPlayHttpClient)
            downloadData.addAll(purchaseHelper.purchase(packageName, versionCode, offerType))
        }
        return downloadData
    }

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int,
        authData: AuthData
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

    suspend fun checkIntegrityService(
        authData: AuthData,
        packageName: String,
        nonce: String,
        droidGuardToken: String
    ): String? {

        val integrityApiKey = "AIzaSyAjf_GZN-B3oyEKTqug3eEcch_4qK3M2Hg"

        return withContext(Dispatchers.IO) {

            Log.i("jklee", "checking integrity with token $droidGuardToken")
            val integrityHelper = IntegrityHelper(authData, integrityApiKey).using(gPlayHttpClient)
            var response = integrityHelper.checkIntegrity(packageName, nonce, Base64.decode(droidGuardToken, 11))
            Log.i("jklee", "checkIntegrity reponse ${response.code}")
            Log.i("jklee", "checkIntegrity successful ${response.isSuccessful}")

            if (!response.isSuccessful) {
                null
            } else {
                IntegrityAnswer.parseFrom(response.responseBytes).tokenWrapper.token.value
            }
        }
    }

    suspend fun getAppDetails(packageName: String, authData: AuthData): App? {
        var appDetails: App?
        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetails = appDetailsHelper.getAppByPackageName(packageName)
        }
        return appDetails
    }

    suspend fun getAppDetails(packageNameList: List<String>, authData: AuthData): List<App> {
        val appDetailsList = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            val appDetailsHelper = AppDetailsHelper(authData).using(gPlayHttpClient)
            appDetailsList.addAll(appDetailsHelper.getAppByPackageName(packageNameList))
        }
        return appDetailsList
    }

    suspend fun getTopApps(
        type: TopChartsHelper.Type,
        chart: TopChartsHelper.Chart,
        authData: AuthData
    ): List<App> {
        val topApps = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            val topChartsHelper = TopChartsHelper(authData).using(gPlayHttpClient)
            topApps.addAll(topChartsHelper.getCluster(type, chart).clusterAppList)
        }
        return topApps
    }

    suspend fun getCategoriesList(type: Category.Type, authData: AuthData): List<Category> {
        val categoryList = mutableListOf<Category>()
        withContext(Dispatchers.IO) {
            val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)
            categoryList.addAll(categoryHelper.getAllCategoriesList(type))
        }
        return categoryList
    }

    /*
     * Get StreamBundle, either from the homeUrl of a category,
     * or from the current StreamBundle's next url.
     *
     * This function will also be used to fetch the next StreamBundle after
     * all StreamCluster's in the current StreamBundle is iterated over.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
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

    /*
     * Get next StreamCluster from currentNextPageUrl.
     * This method is to be called when the scrollview reaches the bottom.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    suspend fun getNextStreamCluster(
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

    suspend fun listApps(browseUrl: String, authData: AuthData): List<App> {
        val list = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            supervisorScope {
                val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)

                var streamBundle: StreamBundle
                var nextStreamBundleUrl = browseUrl

                /*
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
                 *
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

                // TODO: DEAL WITH DUPLICATE AND LESS ITEMS
                /*val streamClusters = categoryHelper.getSubCategoryBundle(browseUrl).streamClusters
                streamClusters.values.forEach {
                    list.addAll(it.clusterAppList)
                }*/
            }
        }
        return list.distinctBy { it.packageName }
    }
}
