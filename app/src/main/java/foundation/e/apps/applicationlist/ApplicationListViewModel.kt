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

package foundation.e.apps.applicationlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.utils.enums.Origin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>> = MutableLiveData()

    private var streamBundle = StreamBundle()
    private var streamCluster = StreamCluster()

    private var clusterPointer = 0

    var isLoading = false

    /**
     * Variable denoting if we can call [getNextStreamCluster] to get a new StreamBundle.
     *
     * Initially set to true, so that we can get the first StreamBundle.
     * Once the first StreamBundle is fetched, this variable value is same
     * as [streamBundle].hasNext().
     *
     * For more explanation on how [streamBundle] and [streamCluster] work, look at the
     * documentation in [getNextDataSet].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private var hasNextStreamBundle = true

    /**
     * Variable denoting if we can call [getNextStreamCluster] to get a new StreamCluster.
     *
     * Initially set to false so that we get a StreamBundle first, because initially
     * [streamCluster] is empty. Once [streamBundle] is fetched and [getAdjustedFirstCluster]
     * is called, this variable value is same as [streamCluster].hasNext().
     *
     * For more explanation on how [streamBundle] and [streamCluster] work, look at the
     * documentation in [getNextDataSet].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private var hasNextStreamCluster = false

    fun getList(category: String, browseUrl: String, authData: AuthData, source: String) {
        if (isLoading) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val appsListData = if (source == "Open Source" || source == "PWA") {
                fusedAPIRepository.getAppsListBasedOnCategory(
                    category,
                    browseUrl,
                    authData,
                    source
                )
            } else {
                fusedAPIRepository.getNextDataSet(authData, browseUrl).apply {
                    addPlaceHolderAppIfNeeded(this)
                }
            }

            appListLiveData.postValue(appsListData)
        }
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isFusedAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ): Boolean {
        return fusedAPIRepository.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)
    }

    /**
     * Add a placeholder app at the end if more data can be loaded.
     * "Placeholder" app shows a simple progress bar in the RecyclerView, indicating that
     * more apps are being loaded.
     *
     * Note that it mutates the [ResultSupreme] object passed to it.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     *
     * @param result object from [getNextDataSet]. Data of this object will be updated
     * if [canLoadMore] is true.
     *
     * @return true if a placeholder app was added, false otherwise.
     */
    private fun addPlaceHolderAppIfNeeded(result: ResultSupreme<List<FusedApp>>): Boolean {
        result.apply {
            if (isSuccess() && canLoadMore()) {
                // Add an empty app at the end if more data can be loaded on scroll
                val newData = data!!.toMutableList()
                newData.add(FusedApp(isPlaceHolder = true))
                setData(newData)
                return true
            }
        }
        return false
    }

    fun loadMore(authData: AuthData, browseUrl: String) {
        viewModelScope.launch {
            if (!isLoading) {
                val lastCount: Int = streamCluster.clusterAppList.size
                val result = fusedAPIRepository.getNextDataSet(authData, browseUrl)
                val newCount = streamCluster.clusterAppList.size
                appListLiveData.postValue(result)
                /*
                 * Check if a placeholder app is to be added at the end.
                 * If yes then post the updated result.
                 * We post this separately as it helps clear any previous placeholder app
                 * and ensures only a single placeholder app is present at the end of the
                 * list, and none at the middle of the list.
                 */
                if (addPlaceHolderAppIfNeeded(result)) {
                    appListLiveData.postValue(result)
                }

                /*
                 * Old count and new count can be same if new StreamCluster has apps which
                 * are already shown, i.e. with duplicate package names.
                 * In that case, if we can load more data, we do it from here itself,
                 * because recyclerview scroll listener will not trigger itself twice
                 * for the same data.
                 */
                if (result.isSuccess() && lastCount == newCount && canLoadMore()) {
                    loadMore(authData, browseUrl)
                }
            }
        }
    }

    /**
     * Get the first StreamBundle object from the category browseUrl, or the subsequent
     * StreamBundle objects from the "streamNextPageUrl" of current [streamBundle].
     * Also resets the [clusterPointer] to 0.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     *
     * @see getNextDataSet
     */
    private suspend fun getNextStreamBundle(
        authData: AuthData,
        browseUrl: String,
    ): ResultSupreme<StreamBundle> {
        return fusedAPIRepository.getNextStreamBundle(authData, browseUrl, streamBundle).apply {
            if (isValidData()) streamBundle = data!!
            hasNextStreamBundle = streamBundle.hasNext()
            clusterPointer = 0
        }
    }

    /**
     * The first StreamCluster inside [streamBundle] may not have a "clusterNextPageUrl".
     * This method tries to fix that.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     *
     * @see getNextDataSet
     */
    private suspend fun getAdjustedFirstCluster(
        authData: AuthData,
    ): ResultSupreme<StreamCluster> {
        return fusedAPIRepository.getAdjustedFirstCluster(authData, streamBundle, clusterPointer)
            .apply {
                if (isValidData()) addNewClusterData(this.data!!)
            }
    }

    /**
     * Get all subsequent StreamCluster of the current [streamBundle].
     * Accumulate the data in [streamCluster].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     *
     * @see getNextDataSet
     */
    private suspend fun getNextStreamCluster(
        authData: AuthData,
    ): ResultSupreme<StreamCluster> {
        return fusedAPIRepository.getNextStreamCluster(authData, streamCluster).apply {
            if (isValidData()) addNewClusterData(this.data!!)
        }
    }

    /**
     * Method to add clusterAppList of [newCluster] to [streamCluster],
     * but properly point to next StreamCluster.
     * Also updates [hasNextStreamCluster].
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private fun addNewClusterData(newCluster: StreamCluster) {
        newCluster.run {
            streamCluster.clusterAppList.apply {
                val addedList = this + newCluster.clusterAppList
                clear()
                addAll(addedList.distinctBy { it.packageName })
            }
            streamCluster.clusterNextPageUrl = this.clusterNextPageUrl
            streamCluster.clusterBrowseUrl = this.clusterBrowseUrl
        }
        hasNextStreamCluster = newCluster.hasNext()
    }

    /**
     * This is how the logic works:
     *
     * StreamBundles are obtained from "browseUrls".
     * Each StreamBundle can contain
     * - some StreamClusters,
     * - point to a following StreamBundle with "streamNextPageUrl"
     *   (checked by StreamBundle.hasNext())
     * Each StreamCluster contain
     * - apps to display
     * - a "clusterBrowseUrl"
     * - can point to a following StreamCluster with new app data using "clusterNextPageUrl"
     *   (checked by StreamCluster.hasNext())
     *
     * -- browseUrl
     *    |
     *    StreamBundle 1 (streamNextPageUrl points to StreamBundle 2)
     *        StreamCluster 1 -> StreamCluster 1.1 -> StreamCluster 1.2 ....
     *        StreamCluster 2 -> StreamCluster 2.1 -> StreamCluster 2.2 ....
     *        StreamCluster 3 -> StreamCluster 3.1 -> StreamCluster 3.2 ....
     *    StreamBundle 2
     *        StreamCluster 4 -> ...
     *        StreamCluster 5 -> ...
     *
     *
     * - "browseUrl": looks like: homeV2?cat=SOCIAL&c=3
     * - "clusterBrowseUrl" (not used here): looks like:
     *   getBrowseStream?ecp=ChWiChIIARIGU09DSUFMKgIIB1ICCAE%3D
     *   getBrowseStream?ecp=CjOiCjAIARIGU09DSUFMGhwKFnJlY3NfdG9waWNfRjkxMjZNYVJ6S1UQOxgDKgIIB1ICCAI%3D
     * - "clusterNextPageUrl" (not directly used here): looks like:
     *   getCluster?enpt=CkCC0_-4AzoKMfqegZ0DKwgIEKGz2kgQuMifuAcQ75So0QkQ6Ijz6gwQzvel8QQQprGBmgUQz938owMQyIeljYQwEAcaFaIKEggBEgZTT0NJQUwqAggHUgIIAQ&n=20
     *
     * ========== Working logic ==========
     *
     * 1. [streamCluster] accumulates all data from all subsequent network calls.
     * Its "clusterNextPageUrl" does point to the next StreamCluster, but its "clusterAppList"
     * contains accumulated data of all previous network calls.
     *
     * 2. [streamBundle] is the same value received from [getNextStreamBundle].
     *
     * 3. Initially [hasNextStreamCluster] is false, denoting [streamCluster] is empty.
     * Initially [clusterPointer] = 0, [streamBundle].streamClusters.size = 0,
     * hence 2nd case also does not execute.
     * However, initially [hasNextStreamBundle] is true, thus [getNextStreamBundle] is called,
     * fetching the first StreamBundle and storing the data in [streamBundle], and getting the first
     * StreamCluster data using [getAdjustedFirstCluster].
     *
     * NOTE: [getAdjustedFirstCluster] is used to fetch StreamCluster 1, 2, 3 .. in the above
     * diagram with help of [clusterPointer]. For subsequent StreamCluster 1.1, 1.2 .. 2.1 ..
     * [getNextStreamCluster] is used.
     *
     * 4. From now onwards,
     * - [hasNextStreamBundle] is as good as [streamBundle].hasNext()
     * - [hasNextStreamCluster] is as good as [streamCluster].hasNext()
     *
     * 5.1. When this method is again called when list reaches the end while scrolling on the UI,
     * if [hasNextStreamCluster] is true, we will get the next StreamCluster under the current
     * StreamBundle object. Once the last StreamCluster is reached, [hasNextStreamCluster] is
     * false, we move to the next case.
     *
     * 5.2. In the step 5.1 we have been traversing along the path StreamCluster 1 -> 1.1 -> 1.2 ..
     * Once that path reaches an end, we need to jump to StreamCluster 2 -> 2.1 -> 2.2 ..
     * This is achieved by the second condition using [clusterPointer]. We increment the
     * pointer and call [getAdjustedFirstCluster] again to start from StreamCluster 2.
     *
     * 5.3. Once we no longer have any more beginning StreamClusters, i.e
     * [clusterPointer] exceeds [streamBundle].streamClusters size, the second condition no
     * longer holds. Now we should try to go to a different StreamBundle.
     * Using the above diagram, we move to StreamBundle 1 -> 2.
     * We check [hasNextStreamBundle]. If that is true, we load the next StreamBundle.
     * This also fetches the first StreamCluster of this bundle, thus re-initialising both
     * [hasNextStreamCluster] and [hasNextStreamBundle].
     *
     * 6. Once we reach the end of all StreamBundles and all StreamClusters, now calling
     * this method makes no network calls.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private suspend fun getNextDataSet(
        authData: AuthData,
        browseUrl: String,
    ): ResultSupreme<List<FusedApp>> {
        isLoading = true

        if (hasNextStreamCluster) {
            getNextStreamCluster(authData).run {
                if (!isSuccess()) {
                    return ResultSupreme.replicate(this, listOf())
                }
            }
        } else if (clusterPointer < streamBundle.streamClusters.size) {
            ++clusterPointer
            getAdjustedFirstCluster(authData).run {
                if (!isSuccess()) {
                    return ResultSupreme.replicate(this, listOf())
                }
            }
        } else if (hasNextStreamBundle) {
            getNextStreamBundle(authData, browseUrl).run {
                if (!isSuccess()) {
                    return ResultSupreme.replicate(this, listOf())
                }
                getAdjustedFirstCluster(authData).run {
                    if (!isSuccess()) {
                        return ResultSupreme.replicate(this, listOf())
                    }
                }
            }
        }
        return fusedAPIRepository.filterRestrictedGPlayApps(authData, streamCluster.clusterAppList)
            .apply {
                isLoading = false
            }
    }

    /**
     * Function is used to check if we can load more data.
     * It is also used to show a loading progress bar at the end of the list.
     */
    fun canLoadMore(): Boolean =
        hasNextStreamCluster || clusterPointer < streamBundle.streamClusters.size || hasNextStreamBundle

    private fun getOrigin(source: String) =
        if (source.contentEquals("Open Source")) Origin.CLEANAPK else Origin.GPLAY

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ) = fusedAPIRepository.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)

    fun hasAnyAppInstallStatusChanged(currentList: List<FusedApp>) =
        fusedAPIRepository.isAnyAppInstallStatusChanged(currentList)
}
