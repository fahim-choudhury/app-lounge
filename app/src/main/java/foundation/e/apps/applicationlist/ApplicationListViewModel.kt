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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.utils.enums.Origin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ApplicationListViewModel @Inject constructor(
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val appListLiveData: MutableLiveData<ResultSupreme<List<FusedApp>>> = MutableLiveData()

    var isLoading = false

    fun getList(category: String, browseUrl: String, authData: AuthData, source: String) {
        Timber.d("===> getlist: $isLoading")
        if (isLoading) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            fusedAPIRepository.getAppList(category, browseUrl, authData, source).apply {
                isLoading = false
                appListLiveData.postValue(this)
                Timber.d("final result: ${this.data?.size}")
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

    /**
     * Add a placeholder app at the end if more data can be loaded.
     * "Placeholder" app shows a simple progress bar in the RecyclerView, indicating that
     * more apps are being loaded.
     *
     * Note that it mutates the [ResultSupreme] object passed to it.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     *
     * @param result object from [getNextDataSet]. Data of this object will be updated
     * if [canLoadMore] is true.
     *
     * @return true if a placeholder app was added, false otherwise.
     */
    private fun addPlaceHolderAppIfNeeded(result: ResultSupreme<List<FusedApp>>): Boolean {
        result.apply {
            if (isSuccess() && fusedAPIRepository.canLoadMore()) {
                // Add an empty app at the end if more data can be loaded on scroll
                val newData = data!!.toMutableList()
                newData.add(FusedApp(isPlaceHolder = true))
                setData(newData)
                return true
            }
        }
        return false
    }

    fun loadMore(authData: AuthData, browseUrl: String) {
        viewModelScope.launch {
            if (isLoading) {
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
                loadMore(authData, browseUrl)
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
