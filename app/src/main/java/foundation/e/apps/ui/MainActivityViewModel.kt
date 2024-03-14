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

package foundation.e.apps.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.R
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.ecloud.EcloudRepository
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.enums.isInitialized
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.preference.AppLoungeDataStore
import foundation.e.apps.data.preference.SessionPreference
import foundation.e.apps.data.preference.getSync
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.workmanager.AppInstallProcessor
import foundation.e.apps.utils.NetworkStatusManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val appLoungeDataStore: AppLoungeDataStore,
    private val applicationRepository: ApplicationRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val appLoungePackageManager: AppLoungePackageManager,
    private val pwaManager: PWAManager,
    private val ecloudRepository: EcloudRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val appInstallProcessor: AppInstallProcessor,
    private val sessionPreference: SessionPreference
) : ViewModel() {

    val tocStatus: LiveData<Boolean> = appLoungeDataStore.tocStatus.asLiveData()

    private val _purchaseAppLiveData: MutableLiveData<FusedDownload> = MutableLiveData()
    val purchaseAppLiveData: LiveData<FusedDownload> = _purchaseAppLiveData
    val isAppPurchased: MutableLiveData<String> = MutableLiveData()
    val purchaseDeclined: MutableLiveData<String> = MutableLiveData()
    lateinit var internetConnection: LiveData<Boolean>

    var gPlayAuthData = AuthData("", "")

    // Downloads
    val downloadList = fusedManagerRepository.getDownloadLiveList()
    private val _errorMessage = MutableLiveData<Exception>()
    val errorMessage: LiveData<Exception> = _errorMessage

    private val _errorMessageStringResource = MutableLiveData<Int>()
    val errorMessageStringResource: LiveData<Int> = _errorMessageStringResource

    lateinit var connectivityManager: ConnectivityManager

    fun getUser(): User {
        return appLoungeDataStore.getUserType()
    }

    fun getUserEmail(): String {
        return appLoungeDataStore.emailData.getSync()
    }

    fun uploadFaultyTokenToEcloud(email: String, description: String = "") {
        viewModelScope.launch {
            ecloudRepository.uploadFaultyEmail(email, description)
        }
    }

    /*
     * Notification functions
     */

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels() {
        fusedManagerRepository.createNotificationChannels()
    }

    /*
     * Download and cancellation functions
     */

    /*
     * Check and display a snack bar if app is paid and user is logged in in anonymous mode.
     * Returns true if the snack bar was displayed, false otherwise.
     *
     * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/266
     */
    fun shouldShowPaidAppsSnackBar(app: Application): Boolean {
        if (!app.isFree && gPlayAuthData.isAnonymous) {
            _errorMessageStringResource.value = R.string.paid_app_anonymous_message
            return true
        }
        return false
    }

    /**
     * Handle various cases of unsupported apps here.
     * Returns true if the [application] is not supported by App Lounge.
     *
     * Pass [alertDialogContext] as null to prevent an alert dialog from being shown to the user.
     * In that case, this method simply works as a validation.
     *
     * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/178
     */
    fun checkUnsupportedApplication(
        application: Application,
        alertDialogContext: Context? = null
    ): Boolean {
        if (!application.filterLevel.isUnFiltered()) {
            alertDialogContext?.let { context ->
                AlertDialog.Builder(context).apply {
                    setTitle(R.string.unsupported_app_title)
                    setMessage(
                        context.getString(
                            R.string.unsupported_app_unreleased,
                            application.name
                        )
                    )
                    setPositiveButton(android.R.string.ok, null)
                }.show()
            }
            return true
        }
        return false
    }

    /**
     * Fetch the filter level of an app and perform some action.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
     */
    fun verifyUiFilter(application: Application, method: () -> Unit) {
        viewModelScope.launch {
            val authData = gPlayAuthData
            if (application.filterLevel.isInitialized()) {
                method()
            } else {
                applicationRepository.getAppFilterLevel(application, authData).run {
                    if (isInitialized()) {
                        application.filterLevel = this
                        method()
                    }
                }
            }
        }
    }

    fun getApplication(app: Application) {
        viewModelScope.launch {
            appInstallProcessor.initAppInstall(app)
        }
    }

    suspend fun updateAwaitingForPurchasedApp(packageName: String): FusedDownload? {
        val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = packageName)
        gPlayAuthData.let {
            if (!it.isAnonymous) {
                appInstallProcessor.enqueueFusedDownload(fusedDownload)
                return fusedDownload
            }
        }
        return null
    }

    suspend fun updateUnavailableForPurchaseDeclined(packageName: String) {
        val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = packageName)
        fusedManagerRepository.updateUnavailable(fusedDownload)
    }

    fun cancelDownload(app: Application) {
        viewModelScope.launch {
            val fusedDownload =
                fusedManagerRepository.getFusedDownload(packageName = app.package_name)
            fusedManagerRepository.cancelDownload(fusedDownload)
        }
    }

    fun setupConnectivityManager(context: Context) {
        internetConnection = NetworkStatusManager.init(context)
    }

    fun updateStatusOfFusedApps(
        applicationList: List<Application>,
        fusedDownloadList: List<FusedDownload>
    ) {
        applicationList.forEach {
            val downloadingItem = fusedDownloadList.find { fusedDownload ->
                fusedDownload.origin == it.origin && (fusedDownload.packageName == it.package_name || fusedDownload.id == it._id)
            }
            it.status =
                downloadingItem?.status ?: applicationRepository.getFusedAppInstallationStatus(it)
        }
    }

    fun updateAppWarningList() {
        viewModelScope.launch {
            blockedAppRepository.fetchUpdateOfAppWarningList()
        }
    }

    fun getAppNameByPackageName(packageName: String): String {
        return appLoungePackageManager.getAppNameFromPackageName(packageName)
    }

    fun getLaunchIntentForPackageName(packageName: String): Intent? {
        return appLoungePackageManager.getLaunchIntent(packageName)
    }

    fun launchPwa(application: Application) {
        pwaManager.launchPwa(application)
    }

    fun updateIgnoreRefreshPreference(ignore: Boolean) {
        sessionPreference.updateIgnoreSessionRefreshPreference(ignore)
    }

    fun shouldRefreshSession(): Boolean {
        return !sessionPreference.shouldIgnoreSessionRefresh()
    }
}
