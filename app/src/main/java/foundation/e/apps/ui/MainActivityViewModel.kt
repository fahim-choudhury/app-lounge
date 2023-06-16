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

package foundation.e.apps.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Base64
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.exceptions.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.R
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.ecloud.EcloudRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.enums.isInitialized
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.preference.DataStoreModule
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val dataStoreModule: DataStoreModule,
    private val fusedAPIRepository: FusedAPIRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val pkgManagerModule: PkgManagerModule,
    private val pwaManagerModule: PWAManagerModule,
    private val ecloudRepository: EcloudRepository,
    private val blockedAppRepository: BlockedAppRepository,
) : ViewModel() {

    val tocStatus: LiveData<Boolean> = dataStoreModule.tocStatus.asLiveData()

    private val _purchaseAppLiveData: MutableLiveData<FusedDownload> = MutableLiveData()
    val purchaseAppLiveData: LiveData<FusedDownload> = _purchaseAppLiveData
    val isAppPurchased: MutableLiveData<String> = MutableLiveData()
    val purchaseDeclined: MutableLiveData<String> = MutableLiveData()

    var gPlayAuthData = AuthData("", "")

    // Downloads
    val downloadList = fusedManagerRepository.getDownloadLiveList()
    var installInProgress = false
    private val _errorMessage = MutableLiveData<Exception>()
    val errorMessage: LiveData<Exception> = _errorMessage

    private val _errorMessageStringResource = MutableLiveData<Int>()
    val errorMessageStringResource: LiveData<Int> = _errorMessageStringResource

    lateinit var connectivityManager: ConnectivityManager

    companion object {
        private const val TAG = "MainActivityViewModel"
    }

    fun getUser(): User {
        return dataStoreModule.getUserType()
    }

    fun getUserEmail(): String {
        return dataStoreModule.getEmail()
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

    fun downloadApp(fusedDownload: FusedDownload) {
        viewModelScope.launch {
            fusedManagerRepository.downloadApp(fusedDownload)
        }
    }

    /*
     * Check and display a snack bar if app is paid and user is logged in in anonymous mode.
     * Returns true if the snack bar was displayed, false otherwise.
     *
     * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/266
     */
    fun shouldShowPaidAppsSnackBar(app: FusedApp): Boolean {
        if (!app.isFree && gPlayAuthData.isAnonymous) {
            _errorMessageStringResource.value = R.string.paid_app_anonymous_message
            return true
        }
        return false
    }

    /**
     * Handle various cases of unsupported apps here.
     * Returns true if the [fusedApp] is not supported by App Lounge.
     *
     * Pass [alertDialogContext] as null to prevent an alert dialog from being shown to the user.
     * In that case, this method simply works as a validation.
     *
     * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/178
     */
    fun checkUnsupportedApplication(
        fusedApp: FusedApp,
        alertDialogContext: Context? = null
    ): Boolean {
        if (!fusedApp.filterLevel.isUnFiltered()) {
            alertDialogContext?.let { context ->
                AlertDialog.Builder(context).apply {
                    setTitle(R.string.unsupported_app_title)
                    setMessage(
                        context.getString(
                            R.string.unsupported_app_unreleased,
                            fusedApp.name
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
    fun verifyUiFilter(fusedApp: FusedApp, method: () -> Unit) {
        viewModelScope.launch {
            val authData = gPlayAuthData
            if (fusedApp.filterLevel.isInitialized()) {
                method()
            } else {
                fusedAPIRepository.getAppFilterLevel(fusedApp, authData).run {
                    if (isInitialized()) {
                        fusedApp.filterLevel = this
                        method()
                    }
                }
            }
        }
    }

    fun getApplication(app: FusedApp) {
        if (shouldShowPaidAppsSnackBar(app)) {
            return
        }

        viewModelScope.launch {
            val fusedDownload: FusedDownload
            try {
                fusedDownload = FusedDownload(
                    app._id,
                    app.origin,
                    app.status,
                    app.name,
                    app.package_name,
                    mutableListOf(),
                    mutableMapOf(),
                    app.status,
                    app.type,
                    app.icon_image_path,
                    app.latest_version_code,
                    app.offer_type,
                    app.isFree,
                    app.originalSize
                )
                updateFusedDownloadWithAppDownloadLink(app, fusedDownload)
            } catch (e: Exception) {
                if (e is ApiException.AppNotPurchased) {
                    handleAppNotPurchased(app)
                    return@launch
                }
                _errorMessage.value = e
                return@launch
            }

            if (fusedDownload.status == Status.INSTALLATION_ISSUE) {
                fusedManagerRepository.clearInstallationIssue(fusedDownload)
            }
            fusedManagerRepository.addDownload(fusedDownload)
        }
    }

    private fun handleAppNotPurchased(
        app: FusedApp
    ) {
        val fusedDownload = FusedDownload(
            app._id,
            app.origin,
            Status.PURCHASE_NEEDED,
            app.name,
            app.package_name,
            mutableListOf(),
            mutableMapOf(),
            app.status,
            app.type,
            app.icon_image_path,
            app.latest_version_code,
            app.offer_type,
            app.isFree,
            app.originalSize
        )
        viewModelScope.launch {
            fusedManagerRepository.addFusedDownloadPurchaseNeeded(fusedDownload)
            _purchaseAppLiveData.postValue(fusedDownload)
        }
    }

    suspend fun updateAwaiting(fusedDownload: FusedDownload) {
        fusedManagerRepository.updateAwaiting(fusedDownload)
    }

    suspend fun updateUnAvailable(fusedDownload: FusedDownload) {
        fusedManagerRepository.updateUnavailable(fusedDownload)
    }

    suspend fun updateAwaitingForPurchasedApp(packageName: String): FusedDownload? {
        val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = packageName)
        gPlayAuthData.let {
            if (!it.isAnonymous) {
                try {
                    fusedAPIRepository.updateFusedDownloadWithDownloadingInfo(
                        it,
                        Origin.GPLAY,
                        fusedDownload
                    )
                } catch (e: ApiException.AppNotPurchased) {
                    e.printStackTrace()
                    return null
                } catch (e: Exception) {
                    e.printStackTrace()
                    _errorMessage.value = e
                    return null
                }
                updateAwaiting(fusedDownload)
                return fusedDownload
            }
        }
        return null
    }

    suspend fun updateUnavailableForPurchaseDeclined(packageName: String) {
        val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = packageName)
        fusedManagerRepository.updateUnavailable(fusedDownload)
    }

    fun cancelDownload(app: FusedApp) {
        viewModelScope.launch {
            val fusedDownload =
                fusedManagerRepository.getFusedDownload(packageName = app.package_name)
            fusedManagerRepository.cancelDownload(fusedDownload)
        }
    }

    private suspend fun updateFusedDownloadWithAppDownloadLink(
        app: FusedApp,
        fusedDownload: FusedDownload
    ) {
        val downloadList = mutableListOf<String>()
        gPlayAuthData.let {
            if (app.type == Type.PWA) {
                downloadList.add(app.url)
                fusedDownload.downloadURLList = downloadList
            } else {
                fusedAPIRepository.updateFusedDownloadWithDownloadingInfo(
                    it,
                    app.origin,
                    fusedDownload
                )

            }
        }
    }

    fun setupConnectivityManager(context: Context) {
        connectivityManager =
            context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager
    }

    val internetConnection =
        callbackFlow {
            if (!this@MainActivityViewModel::connectivityManager.isInitialized) {
                awaitClose { }
                return@callbackFlow
            }

            sendInternetStatus(connectivityManager)
            val networkCallback = getNetworkCallback(this)
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }.asLiveData().distinctUntilChanged()

    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private fun getNetworkCallback(
        callbackFlowScope: ProducerScope<Boolean>,
    ): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                callbackFlowScope.sendInternetStatus(connectivityManager)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                callbackFlowScope.sendInternetStatus(connectivityManager)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                callbackFlowScope.sendInternetStatus(connectivityManager)
            }
        }
    }

    private fun ProducerScope<Boolean>.sendInternetStatus(connectivityManager: ConnectivityManager) {

        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        val hasInternet =
            capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        trySend(hasInternet)
    }

    fun updateStatusOfFusedApps(
        fusedAppList: List<FusedApp>,
        fusedDownloadList: List<FusedDownload>
    ) {
        fusedAppList.forEach {
            val downloadingItem = fusedDownloadList.find { fusedDownload ->
                fusedDownload.origin == it.origin && (fusedDownload.packageName == it.package_name || fusedDownload.id == it._id)
            }
            it.status =
                downloadingItem?.status ?: fusedAPIRepository.getFusedAppInstallationStatus(it)
        }
    }

    fun updateAppWarningList() {
        blockedAppRepository.fetchUpdateOfAppWarningList()
    }

    fun getAppNameByPackageName(packageName: String): String {
        return pkgManagerModule.getAppNameFromPackageName(packageName)
    }

    fun getLaunchIntentForPackageName(packageName: String): Intent? {
        return pkgManagerModule.getLaunchIntent(packageName)
    }

    fun launchPwa(fusedApp: FusedApp) {
        pwaManagerModule.launchPwa(fusedApp)
    }
}
