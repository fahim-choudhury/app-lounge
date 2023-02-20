package foundation.e.apps.api.gplay

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.liveData
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.SearchBundle
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.api.StoreApiRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import foundation.e.apps.login.LoginSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GplayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gPlayHttpClient: GPlayHttpClient,
    private val loginSourceRepository: LoginSourceRepository
) : StoreApiRepository {

    override suspend fun getHomeScreenData(): Any {
        val homeScreenData = mutableMapOf<String, List<App>>()
        val homeElements = mutableMapOf(
            context.getString(R.string.topselling_free_apps) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.topselling_free_games) to mapOf(TopChartsHelper.Chart.TOP_SELLING_FREE to TopChartsHelper.Type.GAME),
            context.getString(R.string.topgrossing_apps) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.topgrossing_games) to mapOf(TopChartsHelper.Chart.TOP_GROSSING to TopChartsHelper.Type.GAME),
            context.getString(R.string.movers_shakers_apps) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.APPLICATION),
            context.getString(R.string.movers_shakers_games) to mapOf(TopChartsHelper.Chart.MOVERS_SHAKERS to TopChartsHelper.Type.GAME),
        )
        homeElements.forEach {
            val chart = it.value.keys.iterator().next()
            val type = it.value.values.iterator().next()
            val result = getTopApps(type, chart, loginSourceRepository.gplayAuth!!)
            homeScreenData[it.key] = result
        }

        return homeScreenData
    }

    override fun getSearchResult(query: String): LiveData<Pair<List<App>, Boolean>> {
        return liveData {
            withContext(Dispatchers.IO) {
                /*
                 * Variable names and logic made same as that of Aurora store.
                 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5171
                 */
                val searchHelper = SearchHelper(loginSourceRepository.gplayAuth!!).using(gPlayHttpClient)
                val searchBundle = searchHelper.searchResults(query)

                val initialReplacedList = mutableListOf<App>()
                val INITIAL_LIMIT = 4

                emitReplacedList(
                    this@liveData,
                    initialReplacedList,
                    INITIAL_LIMIT,
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
        scope: LiveDataScope<Pair<List<App>, Boolean>>,
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
                }
                accumulationList.size == accumulationLimit -> {
                    /*
                     * If initial limit is 4, and we have emitted 4 apps,
                     * for all rest of the apps, emit each app one by one.
                     */
                    scope.emit(Pair(listOf(it), moreToEmit))
                }
            }
        }
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