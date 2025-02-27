/*
 * Copyright (C) 2021-2024 MURENA SAS
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
 *
 */

package foundation.e.apps.ui.application

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.exceptions.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.R
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.shareUri
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.CleanApkException
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.data.parentalcontrol.fdroid.FDroidAntiFeatureRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.download.data.DownloadProgressLD
import foundation.e.apps.ui.application.ShareButtonVisibilityState.Hidden
import foundation.e.apps.ui.application.ShareButtonVisibilityState.Visible
import foundation.e.apps.ui.parentFragment.LoadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicationViewModel @Inject constructor(
    downloadProgressLD: DownloadProgressLD,
    private val applicationRepository: ApplicationRepository,
    private val playStoreRepository: PlayStoreRepository,
    private val appManagerWrapper: AppManagerWrapper,
    private val fDroidAntiFeatureRepository: FDroidAntiFeatureRepository,
) : LoadingViewModel() {

    val applicationLiveData: MutableLiveData<Pair<Application, ResultStatus>> = MutableLiveData()
    val appStatus: MutableLiveData<Status?> = MutableLiveData()
    val downloadProgress = downloadProgressLD
    private val _errorMessageLiveData: MutableLiveData<Int> = MutableLiveData()
    val errorMessageLiveData: MutableLiveData<Int> = _errorMessageLiveData

    private val _shareButtonVisibilityState = MutableStateFlow<ShareButtonVisibilityState>(Hidden)
    val shareButtonVisibilityState = _shareButtonVisibilityState.asStateFlow()

    private val _appContentRatingState = MutableStateFlow(ContentRating())
    val appContentRatingState = _appContentRatingState.asStateFlow()

    fun loadData(
        params: ApplicationLoadingParams,
        retryBlock: (failedObjects: List<AuthObject>) -> Boolean,
    ) {

        if (params.isFdroidDeepLink) {
            getCleanapkAppDetails(params.packageName)
            return
        }

        val gPlayObj = params.authObjectList.find { it is AuthObject.GPlayAuth }

        /*
         * If user is viewing only open source apps, auth object list will not have
         * GPlayAuth, it will only have CleanApkAuth.
         */
        if (gPlayObj == null && params.origin == Origin.GPLAY) {
            _errorMessageLiveData.postValue(R.string.gplay_data_for_oss)
            return
        }

        super.onLoadData(params.authObjectList, { successAuthList, _ ->

            successAuthList.find { it is AuthObject.GPlayAuth }?.run {
                val authData = result.data as? AuthData
                // Usually authdata won't be null, null check is added to avoid forcefully unwrapping
                if (authData == null) {
                    _errorMessageLiveData.postValue(R.string.data_load_error)
                    return@onLoadData
                }

                getApplicationDetails(
                    params.appId,
                    params.packageName,
                    params.isPurchased,
                    authData,
                    params.origin
                )
                return@onLoadData
            }

            successAuthList.find { it is AuthObject.CleanApk }?.run {
                getApplicationDetails(
                    params.appId,
                    params.packageName,
                    params.isPurchased,
                    AuthData("", ""),
                    params.origin
                )
                return@onLoadData
            }
        }, retryBlock)
    }

    fun getApplicationDetails(
        id: String,
        packageName: String,
        isPurchased: Boolean,
        authData: AuthData,
        origin: Origin
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appData =
                    applicationRepository.getApplicationDetails(
                        id,
                        packageName,
                        authData,
                        origin
                    )
                appData.first.isPurchased = isPurchased
                applicationLiveData.postValue(appData)

                updateShareVisibilityState(appData.first.shareUri.toString())
                updateAppContentRatingState(packageName, appData.first.contentRating)

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

    private suspend fun updateAppContentRatingState(
        packageName: String,
        contentRating: ContentRating
    ) {
        // Initially update the state without ID to show the UI immediately
        _appContentRatingState.update { contentRating }

        val ratingWithId = playStoreRepository.getContentRatingWithId(packageName, contentRating)


        // Later, update with a new rating; no visual change in the UI
        val updatedContentRating = contentRating.copy(id = ratingWithId.id)
        _appContentRatingState.update { updatedContentRating }

        applicationLiveData.value?.copy()?.let {
            val application = it.first
            application.contentRating = updatedContentRating
            applicationLiveData.postValue(it)
        }
    }

    private fun updateShareVisibilityState(shareUri: String) {
        val isValidUri = shareUri.isNotBlank()
        _shareButtonVisibilityState.value = if (isValidUri) Visible else Hidden
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
                        applicationLiveData.postValue(this)
                        updateShareVisibilityState(first.shareUri.toString())
                    }
                }
            } catch (e: Exception) {
                _errorMessageLiveData.postValue(R.string.unknown_error)
            }
        }
    }

    fun transformPermsToString(): String {
        var permissionString = ""
        applicationLiveData.value?.first?.let {
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
        return applicationLiveData.value?.first
    }

    fun handleRatingFormat(rating: Double): String {
        return appManagerWrapper.handleRatingFormat(rating)
    }

    suspend fun calculateProgress(progress: DownloadProgress): Pair<Long, Long> {
        return appManagerWrapper.getCalculateProgressWithTotalSize(
            applicationLiveData.value?.first,
            progress
        )
    }

    fun updateApplicationStatus(downloadList: List<AppInstall>) {
        applicationLiveData.value?.first?.let { app ->
            appStatus.value = appManagerWrapper.getDownloadingItemStatus(app, downloadList)
                ?: applicationRepository.getFusedAppInstallationStatus(app)
        }
    }

    fun isOpenSourceSelected() = applicationRepository.isOpenSourceSelected()

    fun isKnownNsfwApp(app: Application): Boolean {
        return app.package_name in fDroidAntiFeatureRepository.fDroidNsfwApps
    }
}

sealed class ShareButtonVisibilityState {
    object Visible : ShareButtonVisibilityState()
    object Hidden : ShareButtonVisibilityState()
}

data class ApplicationLoadingParams(
    val appId: String,
    val packageName: String,
    val origin: Origin,
    val isFdroidDeepLink: Boolean,
    val authObjectList: List<AuthObject>,
    val isPurchased: Boolean
)
