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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>?> = MutableLiveData()

    private var isLoading = false

    private var nextPageUrl: String? = null

    private var currentAuthListObject: List<AuthObject>? = null

    fun loadData(
        category: String,
        source: String,
        authData: AuthData?
    ) {
        getList(category, authData ?: AuthData("", ""), source)
        super.onLoadData(authObjectList, { successAuthList, _ ->

            // if token is refreshed, then reset all data
            if (currentAuthListObject != null && currentAuthListObject != authObjectList) {
                appListLiveData.postValue(ResultSupreme.Success(emptyList()))
                nextPageUrl = null
            }

            if (appListLiveData.value?.data?.isNotEmpty() == true && currentAuthListObject == authObjectList) {
                appListLiveData.postValue(appListLiveData.value)
                return@onLoadData
            }

            this.currentAuthListObject = authObjectList
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
            val result = fusedAPIRepository.getAppsListBasedOnCategory(
                authData,
                category,
                nextPageUrl,
                Source.fromString(source)
            ).apply {
                isLoading = false
            }

            result.data?.let {
                appListLiveData.postValue(ResultSupreme.create(ResultStatus.OK, it.first))
                updateNextPageUrl(it.second)
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

    fun loadMore(authData: AuthData?, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isLoading || authData == null || nextPageUrl.isNullOrEmpty()) {
                return@launch
            }

            isLoading = true
            val result = fusedAPIRepository.getAppsListBasedOnCategory(
                authData,
                category,
                nextPageUrl,
                Source.GPLAY
            )
            isLoading = false

            result.data?.let {
                val appList = appendAppList(it)
                val resultSupreme = ResultSupreme.create(ResultStatus.OK, appList)
                appListLiveData.postValue(resultSupreme)

                updateNextPageUrl(it.second)
            }
        }
    }

    private fun appendAppList(it: Pair<List<FusedApp>, String>): List<FusedApp>? {
        val currentAppList = appListLiveData.value?.data?.toMutableList()
        currentAppList?.removeIf { item -> item.isPlaceHolder }
        return currentAppList?.plus(it.first)
    }

    fun hasAnyAppInstallStatusChanged(currentList: List<FusedApp>) =
        fusedAPIRepository.isAnyAppInstallStatusChanged(currentList)
}
