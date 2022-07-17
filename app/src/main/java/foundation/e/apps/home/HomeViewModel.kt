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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.home.model.HomeChildFusedAppDiffUtil
import foundation.e.apps.utils.enums.ResultStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    /*
     * Hold list of applications, as well as application source type.
     * Source type may change from user selected preference in case of timeout.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    var homeScreenData: MutableLiveData<Pair<List<FusedHome>, ResultStatus>> = MutableLiveData()

    fun getHomeScreenData(authData: AuthData) {
        viewModelScope.launch {
            val screenData = fusedAPIRepository.getHomeScreenData(authData)
            homeScreenData.postValue(screenData)
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

    fun compareNewHomeDataWithOldHomeData(
        newHomeData: List<FusedHome>,
        oldHomeData: List<FusedHome>
    ): Boolean {
        oldHomeData.forEach {
            val fusedHome = newHomeData[oldHomeData.indexOf(it)]
            if (!it.title.contentEquals(fusedHome.title) || !areOldAndNewFusedAppListSame(it, fusedHome)) {
                return true
            }
        }
        return false
    }

    private fun areOldAndNewFusedAppListSame(
        it: FusedHome,
        fusedHome: FusedHome,
    ): Boolean {
        val fusedAppDiffUtil = HomeChildFusedAppDiffUtil()

        it.list.forEach { oldFusedApp ->
            val indexOfOldFusedApp = it.list.indexOf(oldFusedApp)
            val fusedApp = fusedHome.list[indexOfOldFusedApp]
            if (!fusedAppDiffUtil.areContentsTheSame(oldFusedApp, fusedApp)) {
                return false
            }
        }
        return true
    }
}
