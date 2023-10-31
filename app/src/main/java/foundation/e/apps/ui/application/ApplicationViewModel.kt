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

package foundation.e.apps.ui.application

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.exceptions.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.R
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.download.data.DownloadProgressLD
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationViewModel @Inject constructor(
    downloadProgressLD: DownloadProgressLD,
    private val applicationRepository: ApplicationRepository,
    private val fusedManagerRepository: FusedManagerRepository,
) : LoadingViewModel() {

    val application: MutableLiveData<Pair<Application, ResultStatus>> = MutableLiveData()
    val appStatus: MutableLiveData<Status?> = MutableLiveData()
    val downloadProgress = downloadProgressLD
    private val _errorMessageLiveData: MutableLiveData<Int> = MutableLiveData()
    val errorMessageLiveData: MutableLiveData<Int> = _errorMessageLiveData

    fun loadData(
        id: String,
        packageName: String,
        origin: Origin,
        isFdroidLink: Boolean,
        authObjectList: List<AuthObject>,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {

        if (isFdroidLink) {
            getCleanapkAppDetails(packageName)
            return
        }

        val gPlayObj = authObjectList.find { it is AuthObject.GPlayAuth }

        /*
         * If user is viewing only open source apps, auth object list will not have
         * GPlayAuth, it will only have CleanApkAuth.
         */
        if (gPlayObj == null && origin == Origin.GPLAY) {
            _errorMessageLiveData.postValue(R.string.gplay_data_for_oss)
            return
        }

        super.onLoadData(authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                getApplicationDetails(id, packageName, result.data!! as AuthData, origin)
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getApplicationDetails(id, packageName, AuthData("", ""), origin)
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getApplicationDetails(id: String, packageName: String, authData: AuthData, origin: Origin) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appData =
                    applicationRepository.getApplicationDetails(
                        id,
                        packageName,
                        authData,
                        origin
                    )
                application.postValue(appData)

                val status = appData.second

                if (appData.second != ResultStatus.OK) {
                    val exception =
                        if (authData.aasToken.isNotBlank() || authData.authToken.isNotBlank())
                            GPlayException(
                                appData.second == ResultStatus.TIMEOUT,
                                status.message.ifBlank { "Data load error" }
                            )
                        else CleanApkException(
                            appData.second == ResultStatus.TIMEOUT,
                            status.message.ifBlank { "Data load error" }
                        )

                    exceptionsList.add(exception)
                    exceptionsLiveData.postValue(exceptionsList)
                }
            } catch (e: ApiException.AppNotFound) {
                _errorMessageLiveData.postValue(R.string.app_not_found)
            } catch (e: Exception) {
                _errorMessageLiveData.postValue(R.string.unknown_error)
            }
        }
    }

    /*
     * Dedicated method to get app details from cleanapk using package name.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
     */
    fun getCleanapkAppDetails(packageName: String) {
        viewModelScope.launch {
            try {
                applicationRepository.getCleanapkAppDetails(packageName).run {
                    if (this.first.package_name.isBlank()) {
                        _errorMessageLiveData.postValue(R.string.app_not_found)
                    } else {
                        application.postValue(this)
                    }
                }
            } catch (e: Exception) {
                _errorMessageLiveData.postValue(R.string.unknown_error)
            }
        }
    }

    fun transformPermsToString(): String {
        var permissionString = ""
        application.value?.first?.let {
            // Filter list to only keep platform permissions
            val filteredList = it.perms.filter {
                it.startsWith("android.permission.")
            }
            // Remove prefix as we only have platform permissions remaining
            val list = filteredList.map {
                it.replace("[^>]*permission\\.".toRegex(), "")
            }
            // Make it a dialog-friendly string and return it
            permissionString = list.joinToString(separator = "") { "$it<br />" }
        }
        return permissionString
    }

    fun getFusedApp(): Application? {
        return application.value?.first
    }
    fun handleRatingFormat(rating: Double): String {
        return fusedManagerRepository.handleRatingFormat(rating)
    }

    suspend fun calculateProgress(progress: DownloadProgress): Pair<Long, Long> {
        return fusedManagerRepository.getCalculateProgressWithTotalSize(application.value?.first, progress)
    }

    fun updateApplicationStatus(downloadList: List<FusedDownload>) {
        application.value?.first?.let { app ->
            appStatus.value = fusedManagerRepository.getDownloadingItemStatus(app, downloadList)
                ?: applicationRepository.getFusedAppInstallationStatus(app)
        }
    }

    fun isOpenSourceSelected() = applicationRepository.isOpenSourceSelected()
}
