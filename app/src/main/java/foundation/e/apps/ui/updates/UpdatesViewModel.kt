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

package foundation.e.apps.ui.updates

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.data.updates.UpdatesManagerRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesManagerRepository: UpdatesManagerRepository,
    private val fusedAPIRepository: FusedAPIRepository,
    private val preferenceManagerModule: PreferenceManagerModule
) : ViewModel() {

    val updatesList: MutableLiveData<Pair<List<FusedApp>, ResultStatus?>> = MutableLiveData()

    fun loadData(
        authData: AuthData?,
    ) {
        getUpdates(authData ?: AuthData("", ""))
    }

    private fun getUpdates(authData: AuthData?) {
        viewModelScope.launch {
            val updatesResult = if (authData != null)
                updatesManagerRepository.getUpdates(authData)
            else updatesManagerRepository.getUpdatesOSS()
            updatesList.postValue(updatesResult)
        }
    }

    fun checkWorkInfoListHasAnyUpdatableWork(workInfoList: List<WorkInfo>): Boolean {
        workInfoList.forEach { workInfo ->
            if (listOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING
                ).contains(workInfo.state) && checkWorkIsForUpdateByTag(workInfo.tags.toList())
            ) {
                return true
            }
        }
        return false
    }

    private fun checkWorkIsForUpdateByTag(tags: List<String>): Boolean {
        updatesList.value?.let {
            it.first.find { fusedApp -> tags.contains(fusedApp._id) }?.let { foundApp ->
                return listOf(
                    Status.INSTALLED,
                    Status.UPDATABLE
                ).contains(fusedAPIRepository.getFusedAppInstallationStatus(foundApp))
            }
        }
        return false
    }

    fun hasAnyUpdatableApp(): Boolean {
        return updatesList.value?.first?.any { it.status == Status.UPDATABLE || it.status == Status.INSTALLATION_ISSUE } == true
    }

    fun hasAnyPendingAppsForUpdate(): Boolean {
        val pendingStatesForUpdate = listOf(
            Status.QUEUED,
            Status.AWAITING,
            Status.DOWNLOADING,
            Status.DOWNLOADED,
            Status.INSTALLING
        )
        return updatesList.value?.first?.any { pendingStatesForUpdate.contains(it.status) } == true
    }

    fun getUpdateInterval() = preferenceManagerModule.getUpdateInterval()
}
