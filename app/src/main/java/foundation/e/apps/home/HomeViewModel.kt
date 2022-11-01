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

package foundation.e.apps.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.login.AuthObject
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.exceptions.CleanApkException
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.parentFragment.LoadingViewModel
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
    var homeScreenData: MutableLiveData<Pair<List<FusedHome>, ResultStatus>> = MutableLiveData()

    fun loadData(
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getHomeScreenData(result.data!! as AuthData)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getHomeScreenData(AuthData("", ""))
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getHomeScreenData(authData: AuthData) {
        viewModelScope.launch {
            val screenData = fusedAPIRepository.getHomeScreenData(authData)
            homeScreenData.postValue(screenData)

            val status = screenData.second

            if (status != ResultStatus.OK) {
                val exception =
                    if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                        GPlayException(
                            screenData.second == ResultStatus.TIMEOUT,
                            status.message.ifBlank { "Data load error" }
                        )
                    else CleanApkException(
                        screenData.second == ResultStatus.TIMEOUT,
                        status.message.ifBlank { "Data load error" }
                    )

                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
        }
    }

    fun getApplicationCategoryPreference(): String {
        return fusedAPIRepository.getApplicationCategoryPreference()
    }

    fun isFusedHomesEmpty(): Boolean {
        return homeScreenData.value?.first?.let {
            fusedAPIRepository.isFusedHomesEmpty(it)
        } ?: true
    }

    fun isHomeDataUpdated(
        newHomeData: List<FusedHome>,
        oldHomeData: List<FusedHome>
    ) = fusedAPIRepository.isHomeDataUpdated(newHomeData, oldHomeData)

    fun isAnyAppInstallStatusChanged(currentList: List<FusedHome>?): Boolean {
        if (currentList == null) {
            return false
        }

        val appList = mutableListOf<FusedApp>()
        currentList.forEach { appList.addAll(it.list) }
        return fusedAPIRepository.isAnyAppInstallStatusChanged(appList)
    }
}
