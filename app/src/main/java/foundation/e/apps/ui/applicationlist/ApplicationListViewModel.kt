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

package foundation.e.apps.ui.applicationlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : LoadingViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>?> = MutableLiveData()

    var isLoading = false

    var nextPageUrl: String? = null

    fun loadData(
        category: String,
        source: String,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getList(category, result.data!! as AuthData, source)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getList(category, AuthData("", ""), source)
                return@onLoadData
            }
        }, retryBlock)
    }

    private fun getList(category: String, authData: AuthData, source: String) {
        if (isLoading) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val result = fusedAPIRepository.getAppsListBasedOnCategory(authData, category, nextPageUrl, source).apply {
                isLoading = false
            }

            result.data?.let {
                appListLiveData.postValue(ResultSupreme.create(ResultStatus.OK,it.first))
                updateNextPageUrl(it.second)
            }

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

    private fun updateNextPageUrl(nextPageUrl: String?) {
            this.nextPageUrl = nextPageUrl
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

    fun loadMore(gPlayAuth: AuthObject?, category: String) {
        viewModelScope.launch (Dispatchers.IO) {
            Timber.d("Browser url: $category")
            val authData: AuthData? = when {
                gPlayAuth !is AuthObject.GPlayAuth -> null
                !gPlayAuth.result.isSuccess() -> null
                else -> gPlayAuth.result.data!!
            }

            if (isLoading || authData == null || nextPageUrl.isNullOrEmpty()) {
                return@launch
            }

            isLoading = true
            val result = fusedAPIRepository.getAppsListBasedOnCategory(authData, category, nextPageUrl, Source.GPLAY.toString())
            isLoading = false

            result.data?.let {
                val appList = appListLiveData.value?.data?.plus(it.first)
                val resultSupreme = ResultSupreme.create(ResultStatus.OK, appList)
                appListLiveData.postValue(resultSupreme)
                /*
             * Check if a placeholder app is to be added at the end.
             * If yes then post the updated result.
             * We post this separately as it helps clear any previous placeholder app
             * and ensures only a single placeholder app is present at the end of the
             * list, and none at the middle of the list.
             */
                if (result.isSuccess() && it.second.isNotEmpty()) {
                    appListLiveData.postValue(resultSupreme)
                }
                updateNextPageUrl(it.second)
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

}
