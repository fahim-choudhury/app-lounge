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
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.ui.home.model.HomeChildFusedAppDiffUtil
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
) : LoadingViewModel() {

    /*
     * Hold list of applications, as well as application source type.
     * Source type may change from user selected preference in case of timeout.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    var homeScreenData: MutableLiveData<ResultSupreme<List<Home>>> = MutableLiveData()

    var currentHomes: List<Home>? = null

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
            applicationRepository.getHomeScreenData(authData).observe(lifecycleOwner) {
                postHomeResult(it)

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

    private fun postHomeResult(homeResult: ResultSupreme<List<Home>>) {
        if (shouldUpdateResult(homeResult)) {
            homeScreenData.value = homeResult
            this@HomeViewModel.currentHomes = homeResult.data?.map { home -> home.copy() }
        } else { // homeResult is success, but not change is found
            homeScreenData.value = ResultSupreme.Error("No change is found in homepage")
        }
    }

    private fun shouldUpdateResult(homeResult: ResultSupreme<List<Home>>) =
        (homeResult.isSuccess() && hasAnyChange(homeResult.data!!)) || !homeResult.isSuccess()

    @VisibleForTesting
    fun hasAnyChange(
        newHomes: List<Home>,
    ) = currentHomes.isNullOrEmpty() || newHomes.size != currentHomes!!.size || compareWithNewData(
        newHomes
    )

    private fun compareWithNewData(newHomes: List<Home>): Boolean {
        currentHomes!!.forEach {
            val fusedHome = newHomes[currentHomes!!.indexOf(it)]
            if (!it.title.contentEquals(fusedHome.title) || it.id.contentEquals(fusedHome.id)
                || areFusedAppsUpdated(it, fusedHome)
            ) {
                return true
            }
        }

        return false
    }

    private fun areFusedAppsUpdated(
        oldHome: Home,
        newHome: Home,
    ) = oldHome.list.size != newHome.list.size || hasAppListsAnyChange(oldHome, newHome)

    private fun hasAppListsAnyChange(
        oldHome: Home,
        newHome: Home,
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()

        oldHome.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = oldHome.list.indexOf(oldFusedApp)
            val fusedApp = newHome.list[indexOfOldFusedApp]
            if (!fusedAppDiffUtil.areContentsTheSame(oldFusedApp, fusedApp)) {
                return true
            }
        }

        return false
    }

    fun checkAnyChangeInAppStatus() {
        if (this.currentHomes == null) {
            return
        }

        val fusedHomes: MutableList<Home> = mutableListOf()
        checkForChangesInAppStatus(fusedHomes)

        if (fusedHomes.isNotEmpty() && hasAnyChange(fusedHomes)) {
            homeScreenData.value = ResultSupreme.Success(fusedHomes)
        }
    }

    private fun checkForChangesInAppStatus(fusedHomes: MutableList<Home>) {
        var home: Home? = null
        this.currentHomes?.forEach {

            it.list.forEach { application ->
                val status =
                    applicationRepository.getFusedAppInstallationStatus(application)

                if (application.status != status) {
                    application.status = status
                    home = it.copy()
                    // Setting a new id, so that recyclerview can find that this item is changed
                    home?.id = UUID.randomUUID().toString()
                }
            }

            fusedHomes.add(home ?: it)
            home = null
        }
    }
}
