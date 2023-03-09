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

package foundation.e.apps.api.gplay

import android.content.Context
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.api.StoreRepository
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import foundation.e.apps.login.LoginSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GplayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val loginSourceRepository: LoginSourceRepository
) : StoreRepository {

    override suspend fun getHomeScreenData(): Any {
        val homeScreenData = mutableMapOf<String, List<App>>()
        val homeElements = createTopChartElements()

        homeElements.forEach {
            val chart = it.value.keys.iterator().next()
            val type = it.value.values.iterator().next()
            val result = getTopApps(type, chart, loginSourceRepository.gplayAuth!!)
            homeScreenData[it.key] = result
        }

        return homeScreenData
    }

    private fun createTopChartElements() = mutableMapOf(
        context.getString(R.string.topselling_free_apps) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.topselling_free_games) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.GAME),
        context.getString(R.string.topgrossing_apps) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.topgrossing_games) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.GAME),
        context.getString(R.string.movers_shakers_apps) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.APPLICATION),
        context.getString(R.string.movers_shakers_games) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.GAME),
    )

    override suspend fun getSearchResult(query: String): Flow<Pair<List<App>, Boolean>> {
        return flow {
            /*
             * Variable names and logic made same as that of Aurora store.
             * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
             */
            val searchHelper =
                SearchHelper(loginSourceRepository.gplayAuth!!).using(gPlayHttpClient)
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
            val searchHelper =
                SearchHelper(loginSourceRepository.gplayAuth!!).using(gPlayHttpClient)
            searchData.addAll(searchHelper.searchSuggestions(query))
        }
        return searchData.filter { it.suggestedQuery.isNotBlank() }
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
}
