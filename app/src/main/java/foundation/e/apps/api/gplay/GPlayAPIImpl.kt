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
import com.aurora.gplayapi.helpers.AuthValidator
import com.aurora.gplayapi.helpers.CategoryHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.StreamHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.api.gplay.token.TokenRepository
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GPlayAPIImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenRepository: TokenRepository,
    private val dataStoreModule: DataStoreModule,
    private val gPlayHttpClient: GPlayHttpClient
) {

    // TODO: DON'T HARDCODE DISPATCHERS IN ANY METHODS
    suspend fun fetchAuthData() = withContext(Dispatchers.IO) {
        val data = async { tokenRepository.getAuthData() }
        data.await()?.let {
            it.locale = context.resources.configuration.locales[0] // update locale with the default locale from settings
            dataStoreModule.saveCredentials(it)
        }
    }

    fun fetchAuthData(email: String, aasToken: String): AuthData {
        return tokenRepository.getAuthData(email, aasToken)
    }

    suspend fun validateAuthData(authData: AuthData): Boolean {
        var validity: Boolean
        withContext(Dispatchers.IO) {
            val authValidator = AuthValidator(authData).using(gPlayHttpClient)
            validity = authValidator.isValid()
        }
        return validity
    }

    suspend fun getSearchSuggestions(query: String, authData: AuthData): List<SearchSuggestEntry> {
        val searchData = mutableListOf<SearchSuggestEntry>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
    }

    suspend fun getSearchResults(query: String, authData: AuthData): List<App> {
        val searchData = mutableListOf<App>()
        withContext(Dispatchers.IO) {
            val searchHelper = SearchHelper(authData).using(gPlayHttpClient)
            val searchResult = searchHelper.searchResults(query)
            searchData.addAll(searchResult.appList)

            // Fetch more results in case the given result is a promoted app
            if (searchData.size == 1) {
                val bundleSet: MutableSet<SearchBundle.SubBundle> = searchResult.subBundles
                do {
                    val searchBundle = searchHelper.next(bundleSet)
                    if (searchBundle.appList.isNotEmpty()) {
                        searchData.addAll(searchBundle.appList)
                    }
                    bundleSet.apply {
                        clear()
                        addAll(searchBundle.subBundles)
                    }
                } while (bundleSet.isNotEmpty())
            }
        }
        return searchData
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

    /**
     * Get list of "clusterBrowseUrl" which can be used to get [StreamCluster] objects which
     * have "clusterNextPageUrl" to get subsequent [StreamCluster] objects.
     *
     * * -- browseUrl
     *    |
     *    StreamBundle 1 (streamNextPageUrl points to StreamBundle 2)
     *        clusterBrowseUrl 1 -> clusterNextPageUrl 1.1 -> clusterNextPageUrl -> 1.2 ....
     *        clusterBrowseUrl 2 -> clusterNextPageUrl 2.1 -> clusterNextPageUrl -> 2.2 ....
     *        clusterBrowseUrl 3 -> clusterNextPageUrl 3.1 -> clusterNextPageUrl -> 3.2 ....
     *    StreamBundle 2
     *        clusterBroseUrl 4 -> ...
     *        clusterBroseUrl 5 -> ...
     *
     * This function returns the clusterBrowseUrls 1,2,3,4,5...
     */
    suspend fun listAppCategoryUrls(browseUrl: String, authData: AuthData): List<String> {
        val urlList = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            supervisorScope {

                val categoryHelper = CategoryHelper(authData).using(gPlayHttpClient)

                var streamBundle: StreamBundle
                var nextStreamBundleUrl = browseUrl

                do {
                    streamBundle = categoryHelper.getSubCategoryBundle(nextStreamBundleUrl)
                    val streamClusters = streamBundle.streamClusters.values

                    urlList.addAll(streamClusters.map { it.clusterBrowseUrl })
                    nextStreamBundleUrl = streamBundle.streamNextPageUrl
                } while (nextStreamBundleUrl.isNotBlank())
            }
        }

        return urlList.distinct().filter { it.isNotBlank() }
    }

    /**
     * Accept a [browseUrl] of type "clusterBrowseUrl" or "clusterNextPageUrl".
     * Fetch a StreamCluster from the [browseUrl] and return pair of:
     * - List od apps to display.
     * - String url "clusterNextPageUrl" pointing to next StreamCluster. This can be blank (not null).
     */
    suspend fun getAppsAndNextClusterUrl(browseUrl: String, authData: AuthData): Pair<List<App>, String> {
        val streamCluster: StreamCluster
        withContext(Dispatchers.IO) {
            supervisorScope {
                val streamHelper = StreamHelper(authData).using(gPlayHttpClient)
                val browseResponse = streamHelper.getBrowseStreamResponse(browseUrl)

                streamCluster = if (browseResponse.contentsUrl.isNotEmpty()) {
                    streamHelper.getNextStreamCluster(browseResponse.contentsUrl)
                } else if (browseResponse.hasBrowseTab()) {
                    streamHelper.getNextStreamCluster(browseResponse.browseTab.listUrl)
                } else {
                    StreamCluster()
                }
            }
        }
        return Pair(streamCluster.clusterAppList, streamCluster.clusterNextPageUrl)
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
