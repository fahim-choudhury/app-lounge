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
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository
) : LoadingViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<Application>>?> = MutableLiveData()

    private var isLoading = false

    private var nextPageUrl: String? = null

    private var currentAuthListObject: List<AuthObject>? = null

    fun loadData(
        category: String,
        source: String,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
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
            val result = applicationRepository.getAppsListBasedOnCategory(
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

            if (!result.isSuccess()) {
                val exception = getException(authData, result)
                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
        }
    }

    private fun getException(
        authData: AuthData,
        result: ResultSupreme<Pair<List<Application>, String>>
    ) = if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank()) {
        GPlayException(
            result.isTimeout(),
            result.message.ifBlank { "Data load error" }
        )
    } else {
        CleanApkException(
            result.isTimeout(),
            result.message.ifBlank { "Data load error" }
        )
    }

    private fun updateNextPageUrl(nextPageUrl: String?) {
        this.nextPageUrl = nextPageUrl
    }

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        return applicationRepository.isAnyFusedAppUpdated(newApplications, oldApplications)
    }

    fun loadMore(gPlayAuth: AuthObject?, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val authData: AuthData? = when {
                gPlayAuth !is AuthObject.GPlayAuth -> null
                !gPlayAuth.result.isSuccess() -> null
                else -> gPlayAuth.result.data!!
            }

            if (isLoading || authData == null || nextPageUrl.isNullOrEmpty()) {
                return@launch
            }

            isLoading = true
            val result = applicationRepository.getAppsListBasedOnCategory(
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

    private fun appendAppList(it: Pair<List<Application>, String>): List<Application>? {
        val currentAppList = appListLiveData.value?.data?.toMutableList()
        currentAppList?.removeIf { item -> item.isPlaceHolder }
        return currentAppList?.plus(it.first)
    }

    fun hasAnyAppInstallStatusChanged(currentList: List<Application>) =
        applicationRepository.isAnyAppInstallStatusChanged(currentList)
}
