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
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val appListLiveData: MutableLiveData<Pair<List<FusedApp>, ResultStatus?>> = MutableLiveData()

    private var lastBrowseUrl = String()

    private val playStoreCategoryUrls = mutableListOf<String>()
    private var categoryUrlsPointer = 0

    private var nextClusterUrl = String()

    fun getPlayStoreAppsOnScroll(browseUrl: String, authData: AuthData) {
        viewModelScope.launch {
            /*
             * Init condition.
             * If category urls are empty or browseUrl has changed, get new category urls.
             */
            if (playStoreCategoryUrls.isEmpty() || browseUrl != lastBrowseUrl) {
                categoryUrlsPointer = 0
                playStoreCategoryUrls.clear()
                playStoreCategoryUrls.addAll(
                    fusedAPIRepository.getPlayStoreAppCategoryUrls(
                        browseUrl.apply { lastBrowseUrl = this },
                        authData
                    )
                )
            }

            /*
             * This is the new list that will be set to the adapter.
             * Add existing apps now and add additional apps later.
             */
            val newList = mutableListOf<FusedApp>().apply {
                appListLiveData.value?.first?.let { addAll(it) }
            }

            /**
             * There are four types of urls we are dealing with here.
             * - "browseUrl": looks like: homeV2?cat=SOCIAL&c=3
             * - "category urls" or "clusterBrowseUrl":
             *   Stored in [playStoreCategoryUrls]. looks like:
             *   getBrowseStream?ecp=ChWiChIIARIGU09DSUFMKgIIB1ICCAE%3D
             *   getBrowseStream?ecp=CjOiCjAIARIGU09DSUFMGhwKFnJlY3NfdG9waWNfRjkxMjZNYVJ6S1UQOxgDKgIIB1ICCAI%3D
             * - "clusterNextPageUrl": looks like:
             *   getCluster?enpt=CkCC0_-4AzoKMfqegZ0DKwgIEKGz2kgQuMifuAcQ75So0QkQ6Ijz6gwQzvel8QQQprGBmgUQz938owMQyIeljYQwEAcaFaIKEggBEgZTT0NJQUwqAggHUgIIAQ&n=20
             * - "streamNextPageUrl" - not being used in this method.
             *
             * StreamBundles are obtained from "browseUrls".
             * Each StreamBundle can contain StreamClusters,
             * (and point to a following StreamBundle with "streamNextPageUrl" - which is not being used here)
             * Each StreamCluster contain
             * - apps to display
             * - a "clusterBrowseUrl"
             * - can point to a following StreamCluster with new app data using "clusterNextPageUrl".
             *
             * -- browseUrl
             *    |
             *    StreamBundle 1 (streamNextPageUrl points to StreamBundle 2)
             *        clusterBrowseUrl 1 -> clusterNextPageUrl 1.1 -> clusterNextPageUrl -> 1.2 ....
             *        clusterBrowseUrl 2 -> clusterNextPageUrl 2.1 -> clusterNextPageUrl -> 2.2 ....
             *        clusterBrowseUrl 3 -> clusterNextPageUrl 3.1 -> clusterNextPageUrl -> 3.2 ....
             *    StreamBundle 2
             *        clusterBroseUrl 4 -> ...
             *        clusterBroseUrl 5 -> ...
             *
             * [playStoreCategoryUrls] contains all clusterBrowseUrl 1,2,3 as well as 4,5 ...
             *
             * Hence we need to go over both "clusterBrowseUrl" (i.e. [playStoreCategoryUrls])
             * as well as available "clusterNextPageUrl".
             * The [FusedAPIRepository.getPlayStoreAppCategoryUrls] returns "clusterNextPageUrl"
             * in its result (along with list of apps from a StreamCluster.)
             *
             * Case 1: Initially [nextClusterUrl] will be empty. In that case get the first "clusterBrowseUrl".
             * Case 2: After fetching first cluster from getAppsAndNextClusterUrl(),
             *         nextClusterUrl will be set to a valid "clusterNextPageUrl",
             *         then this block will not run.
             * Case 3: If at any point, the return from getAppsAndNextClusterUrl() below does not
             *         return non-blank "clusterNextPageUrl", then take the next "clusterBrowseUrl"
             *         from playStoreCategoryUrls.
             * Case 4: All the above cases do not run. This means all available data has been fetched.
             *
             * [nextClusterUrl] can thus take value of "clusterBrowseUrl" as well as "clusterNextPageUrl"
             */
            if (nextClusterUrl.isBlank()) {
                nextClusterUrl = playStoreCategoryUrls.getOrElse(categoryUrlsPointer++) { String() }
            }

            if (nextClusterUrl.isNotBlank()) {
                fusedAPIRepository.getAppsAndNextClusterUrl(nextClusterUrl, authData).run {
                    val existingPackageNames = newList.map { it.package_name }
                    newList.addAll(first.filter { it.package_name !in existingPackageNames })
                    appListLiveData.postValue(Pair(newList, third))
                    nextClusterUrl = second // set the next "clusterNextPageUrl"
                }
            }
        }
    }

    fun getList(category: String, browseUrl: String, authData: AuthData, source: String) {
        if (appListLiveData.value?.first?.isNotEmpty() == true) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val appsListData = fusedAPIRepository.getAppsListBasedOnCategory(
                category,
                browseUrl,
                authData,
                source
            )

            if (appsListData.second != ResultStatus.OK) {
                appListLiveData.postValue(Pair(listOf(), appsListData.second))
                return@launch
            }

            val applicationDetailsWithStatus = if (!source.contentEquals("PWA")) {
                /*
                 * Optimization: packageNames were not used anywhere else,
                 * hence moved here.
                 */
                val packageNames = appsListData.first.map { it.package_name }
                fusedAPIRepository.getApplicationDetails(
                    packageNames, authData,
                    getOrigin(source)
                )
            } else {
                /*
                 * Optimization: Old code was same as the one called above.
                 */
                appsListData
            }

            appListLiveData.postValue(applicationDetailsWithStatus)
        }
    }

    private fun getOrigin(source: String) =
        if (source.contentEquals("Open Source")) Origin.CLEANAPK else Origin.GPLAY
}
