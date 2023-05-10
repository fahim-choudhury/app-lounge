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

import android.text.format.Formatter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.helpers.CategoryHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.Ratings
import foundation.e.apps.login.AuthObject
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.exceptions.CleanApkException
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : LoadingViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>?> = MutableLiveData()

    var isLoading = false

    var streamBundle: StreamBundle = StreamBundle()

    lateinit var homeUrl: String

    lateinit var categoryHelper: CategoryHelper

    fun loadData(
        category: String,
        browseUrl: String,
        source: String,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getList(category, browseUrl, result.data!! as AuthData, source)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getList(category, browseUrl, AuthData("", ""), source)
                return@onLoadData
            }
        }, retryBlock)
    }

    private fun getList(category: String, browseUrl: String, authData: AuthData, source: String) {
        categoryHelper = CategoryHelper(authData)
        homeUrl = browseUrl
        if (isLoading) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
//            val result = fusedAPIRepository.getAppList(category, browseUrl, authData, source).apply {
//                isLoading = false
//            }
//            appListLiveData.postValue(result)

            try {
                if (!streamBundle.hasCluster() || streamBundle.hasNext()) {
                    //Fetch new stream bundle
                    var appList = fetchAppList()
                    val fusedApps = appList.map { it.transformToFusedApp() }.toMutableList()
//                    appList = fetchAppList()
//                    fusedApps.addAll(appList.map { it.transformToFusedApp() }.toMutableList())
                    appListLiveData.postValue(ResultSupreme.create(ResultStatus.OK, fusedApps))
                    //Post updated to UI
                } else {

                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }

//            if (!result.isSuccess()) {
//                val exception =
//                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
//                        GPlayException(
//                            result.isTimeout(),
//                            result.message.ifBlank { "Data load error" }
//                        )
//                    else CleanApkException(
//                        result.isTimeout(),
//                        result.message.ifBlank { "Data load error" }
//                    )
//
//                exceptionsList.add(exception)
//                exceptionsLiveData.postValue(exceptionsList)
//            }
        }
    }

    private fun fetchAppList(): MutableList<App> {
        val newBundle = getCategoryStreamBundle(
            streamBundle.streamNextPageUrl
        )

        //Update old bundle
        streamBundle.apply {
            streamClusters.putAll(newBundle.streamClusters)
            streamNextPageUrl = newBundle.streamNextPageUrl
        }
        var appList = mutableListOf<App>()
        streamBundle.streamClusters.values.forEach {
            appList.addAll(it.clusterAppList)
        }
        return appList
    }

    private fun App.transformToFusedApp(): FusedApp {
        val app = FusedApp(
            _id = this.id.toString(),
            author = this.developerName,
            category = this.categoryName,
            description = this.description,
            perms = this.permissions,
            icon_image_path = this.iconArtwork.url,
            last_modified = this.updatedOn,
            latest_version_code = this.versionCode,
            latest_version_number = this.versionName,
            name = this.displayName,
            other_images_path = this.screenshots.transformToList(),
            package_name = this.packageName,
            ratings = Ratings(
                usageQualityScore =
                this.labeledRating.run {
                    if (isNotEmpty()) {
                        this.replace(",", ".").toDoubleOrNull() ?: -1.0
                    } else -1.0
                }
            ),
            offer_type = this.offerType,
            origin = Origin.GPLAY,
            shareUrl = this.shareUrl,
            originalSize = this.size,
            appSize = "",
            isFree = this.isFree,
            price = this.price,
            restriction = this.restriction,
        )
        return app
    }

    private fun MutableList<Artwork>.transformToList(): List<String> {
        val list = mutableListOf<String>()
        this.forEach {
            list.add(it.url)
        }
        return list
    }



    private fun getCategoryStreamBundle(
        nextPageUrl: String
    ): StreamBundle {
        return if (streamBundle.streamClusters.isEmpty())
            categoryHelper.getSubCategoryBundle(homeUrl)
        else
            categoryHelper.getSubCategoryBundle(nextPageUrl)
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

    fun loadMore(gPlayAuth: AuthObject?, browseUrl: String) {
        viewModelScope.launch {

            val authData: AuthData? = when {
                gPlayAuth !is AuthObject.GPlayAuth -> null
                !gPlayAuth.result.isSuccess() -> null
                else -> gPlayAuth.result.data!!
            }

            if (isLoading || authData == null) {
                return@launch
            }

            isLoading = true
            val result = fusedAPIRepository.loadMore(authData, browseUrl)
            isLoading = false
            appListLiveData.postValue(result.first)
            /*
             * Check if a placeholder app is to be added at the end.
             * If yes then post the updated result.
             * We post this separately as it helps clear any previous placeholder app
             * and ensures only a single placeholder app is present at the end of the
             * list, and none at the middle of the list.
             */
            if (fusedAPIRepository.addPlaceHolderAppIfNeeded(result.first)) {
                appListLiveData.postValue(result.first)
            }

            /*
             * Old count and new count can be same if new StreamCluster has apps which
             * are already shown, i.e. with duplicate package names.
             * In that case, if we can load more data, we do it from here itself,
             * because recyclerview scroll listener will not trigger itself twice
             * for the same data.
             */
            if (result.first.isSuccess() && !result.second && fusedAPIRepository.canLoadMore()) {
                loadMore(gPlayAuth, browseUrl)
            }
        }
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyAppUpdated(
        newFusedApps: List<FusedApp>,
        oldFusedApps: List<FusedApp>
    ) = fusedAPIRepository.isAnyFusedAppUpdated(newFusedApps, oldFusedApps)

    fun hasAnyAppInstallStatusChanged(currentList: List<FusedApp>) =
        fusedAPIRepository.isAnyAppInstallStatusChanged(currentList)

    override fun onCleared() {
        fusedAPIRepository.clearData()
        super.onCleared()
    }
}
