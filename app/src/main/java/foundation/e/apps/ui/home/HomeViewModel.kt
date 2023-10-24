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

package foundation.e.apps.ui.home

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.ui.home.model.HomeChildFusedAppDiffUtil
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository,
) : LoadingViewModel() {

    /*
     * Hold list of applications, as well as application source type.
     * Source type may change from user selected preference in case of timeout.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    var homeScreenData: MutableLiveData<ResultSupreme<List<FusedHome>>> = MutableLiveData()

    var currentList: List<FusedHome>? = null

    fun loadData(
        authObjectList: List<AuthObject>,
        lifecycleOwner: LifecycleOwner,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getHomeScreenData(result.data!! as AuthData, lifecycleOwner)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getHomeScreenData(AuthData("", ""), lifecycleOwner)
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getHomeScreenData(
        authData: AuthData,
        lifecycleOwner: LifecycleOwner,
    ) {
        viewModelScope.launch {
            fusedAPIRepository.getHomeScreenData(authData).observe(lifecycleOwner) {

                if (it.isSuccess() && !hasAnyChange(it.data!!)) {
                    this@HomeViewModel.currentList = it.data
                    homeScreenData.postValue(ResultSupreme.Error("No change in data!"))
                    return@observe
                }

                homeScreenData.postValue(it)
                if (it.isSuccess()) {
                    return@observe
                }

                val exception =
                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                        GPlayException(
                            it.isTimeout(),
                            it.message.ifBlank { "Data load error" }
                        )
                    else CleanApkException(
                        it.isTimeout(),
                        it.message.ifBlank { "Data load error" }
                    )

                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
        }
    }

    @VisibleForTesting
    fun hasAnyChange(
        newHomeData: List<FusedHome>,
    ): Boolean {
        if (currentList.isNullOrEmpty() || newHomeData.size != currentList!!.size) {
            return true
        }

        currentList!!.forEach {
            val fusedHome = newHomeData[currentList!!.indexOf(it)]
            if (!it.title.contentEquals(fusedHome.title) || areFusedAppsUpdated(it, fusedHome)) {
                return true
            }
        }
        return false
    }

    private fun areFusedAppsUpdated(
        oldFusedHome: FusedHome,
        newFusedHome: FusedHome,
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()
        if (oldFusedHome.list.size != newFusedHome.list.size) {
            return true
        }

        oldFusedHome.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = oldFusedHome.list.indexOf(oldFusedApp)
            val fusedApp = newFusedHome.list[indexOfOldFusedApp]
            if (!fusedAppDiffUtil.areContentsTheSame(oldFusedApp, fusedApp)) {
                return true
            }
        }
        return false
    }

    fun isAnyAppInstallStatusChanged(currentList: List<FusedHome>?): Boolean {
        if (currentList == null) {
            return false
        }

        val appList = mutableListOf<FusedApp>()
        currentList.forEach { appList.addAll(it.list) }
        return fusedAPIRepository.isAnyAppInstallStatusChanged(appList)
    }
}
