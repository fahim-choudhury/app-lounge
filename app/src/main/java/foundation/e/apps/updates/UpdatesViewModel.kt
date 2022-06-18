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

package foundation.e.apps.updates

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.updates.manager.UpdatesManagerRepository
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesManagerRepository: UpdatesManagerRepository,
    private val fusedAPIRepository: FusedAPIRepository
) : ViewModel() {

    val updatesList: MutableLiveData<Pair<List<FusedApp>, ResultStatus?>> = MutableLiveData()

    fun getUpdates(authData: AuthData) {
        viewModelScope.launch {
            val updatesResult = updatesManagerRepository.getUpdates(authData)
            updatesList.postValue(
                Pair(
                    updatesResult.first.filter { !(!it.isFree && authData.isAnonymous) },
                    updatesResult.second
                )
            )
        }
    }

    suspend fun checkWorkInfoListHasAnyUpdatableWork(workInfoList: List<WorkInfo>): Boolean {
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

    fun getApplicationCategoryPreference(): String {
        return updatesManagerRepository.getApplicationCategoryPreference()
    }
}
