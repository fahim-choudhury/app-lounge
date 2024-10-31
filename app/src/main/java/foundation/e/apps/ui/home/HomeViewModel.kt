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
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.ui.applicationlist.ApplicationDiffUtil
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
) : LoadingViewModel() {

    @Inject
    lateinit var appLoungePreference: AppLoungePreference

    /*
     * Hold list of applications, as well as application source type.
     * Source type may change from user selected preference in case of timeout.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    var homeScreenData: MutableLiveData<ResultSupreme<List<Home>>> = MutableLiveData()

    var currentHomes: List<Home>? = null

    private var previousSources = emptyList<Boolean>()

   fun hasData(): Boolean {
       return homeScreenData.value?.data?.isNotEmpty() ?: false
   }

    fun loadData(
        authObjectList: List<AuthObject>,
        lifecycleOwner: LifecycleOwner,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getHomeScreenData(lifecycleOwner)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getHomeScreenData(lifecycleOwner)
                return@onLoadData
            }
        }, retryBlock)
    }

    fun haveSourcesChanged(): Boolean {
        val sources = listOf(
            appLoungePreference.isGplaySelected(),
            appLoungePreference.isOpenSourceSelected(),
            appLoungePreference.isPWASelected()
        )

        if (sources == previousSources) {
            return false
        }

        previousSources = sources
        return true
    }

    private fun getHomeScreenData(
        lifecycleOwner: LifecycleOwner,
    ) {
        viewModelScope.launch {
            applicationRepository.getHomeScreenData().observe(lifecycleOwner) {
                postHomeResult(it)

                if (it.isSuccess()) {
                    return@observe
                }
            }
        }
    }

    private fun postHomeResult(homeResult: ResultSupreme<List<Home>>) {
        if (shouldUpdateResult(homeResult)) {
            homeScreenData.value = homeResult
            // Here, homeResult.data is a mutableList which can be changed anytime.
            // That's why we're setting copy of the list, so that currentHomes isn't changed,
            // when homeresult.data is changed.
            currentHomes = homeResult.data?.map { it.copy() }
            return
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
        currentHomes?.forEach {
            val fusedHome = newHomes[currentHomes!!.indexOf(it)]

            if (!it.title.contentEquals(fusedHome.title) || !it.id.contentEquals(fusedHome.id)
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
        val fusedAppDiffUtil = ApplicationDiffUtil()

        oldHome.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = oldHome.list.indexOf(oldFusedApp)
            val fusedApp = newHome.list[indexOfOldFusedApp]

            if (!fusedAppDiffUtil.areContentsTheSame(oldFusedApp, fusedApp)) {
                return true
            }
        }

        return false
    }
}
