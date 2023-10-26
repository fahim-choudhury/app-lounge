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
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.ApplicationRepository
import foundation.e.apps.data.fused.data.Application
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.data.updates.UpdatesManagerRepository
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updatesManagerRepository: UpdatesManagerRepository,
    private val applicationRepository: ApplicationRepository,
    private val preferenceManagerModule: PreferenceManagerModule
) : LoadingViewModel() {

    val updatesList: MutableLiveData<Pair<List<Application>, ResultStatus?>> = MutableLiveData()

    fun loadData(
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {
        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getUpdates(result.data!! as AuthData)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getUpdates(AuthData("", ""))
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getUpdates(authData: AuthData?) {
        viewModelScope.launch {
            val updatesResult = if (authData != null)
                updatesManagerRepository.getUpdates(authData)
            else updatesManagerRepository.getUpdatesOSS()
            updatesList.postValue(updatesResult)

            val status = updatesResult.second

            if (status != ResultStatus.OK) {
                val exception =
                    if (authData != null &&
                        (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                    ) {
                        GPlayException(
                            updatesResult.second == ResultStatus.TIMEOUT,
                            status.message.ifBlank { "Data load error" }
                        )
                    } else CleanApkException(
                        updatesResult.second == ResultStatus.TIMEOUT,
                        status.message.ifBlank { "Data load error" }
                    )

                exceptionsList.add(exception)
                exceptionsLiveData.postValue(exceptionsList)
            }
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
                ).contains(applicationRepository.getFusedAppInstallationStatus(foundApp))
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
