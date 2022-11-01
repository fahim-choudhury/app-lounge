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
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.login.AuthObject
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

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>> = MutableLiveData()

    var isLoading = false

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

    fun getList(category: String, browseUrl: String, authData: AuthData, source: String) {
        if (isLoading) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val result = fusedAPIRepository.getAppList(category, browseUrl, authData, source).apply {
                isLoading = false
            }
            appListLiveData.postValue(result)

            if (!result.isSuccess()) {
                val exception =
                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                        GPlayException(
                            result.isTimeout(),
                            result.message.ifBlank { "Data load error" }
                        )
                    else CleanApkException(
                        result.isTimeout(),
                        result.message.ifBlank { "Data load error" }
                    )

                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
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
            appListLiveData.postValue(result.first!!)
            /*
             * Check if a placeholder app is to be added at the end.
             * If yes then post the updated result.
             * We post this separately as it helps clear any previous placeholder app
             * and ensures only a single placeholder app is present at the end of the
             * list, and none at the middle of the list.
             */
            if (fusedAPIRepository.addPlaceHolderAppIfNeeded(result.first)) {
                appListLiveData.postValue(result.first!!)
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
