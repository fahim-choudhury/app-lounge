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

package foundation.e.apps

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.exceptions.ApiException
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.cleanapk.blockedApps.BlockedAppRepository
import foundation.e.apps.api.ecloud.EcloudRepository
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.gplay.utils.AC2DMTask
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.modules.CommonUtilsModule.timeoutDurationInMillis
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.beryukhov.reactivenetwork.ReactiveNetwork
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val gson: Gson,
    private val dataStoreModule: DataStoreModule,
    private val fusedAPIRepository: FusedAPIRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val pkgManagerModule: PkgManagerModule,
    private val ecloudRepository: EcloudRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val aC2DMTask: AC2DMTask,
) : ViewModel() {

    val authDataJson: LiveData<String> = dataStoreModule.authData.asLiveData()
    val tocStatus: LiveData<Boolean> = dataStoreModule.tocStatus.asLiveData()
    val userType: LiveData<String> = dataStoreModule.userType.asLiveData()

    private var _authData: MutableLiveData<AuthData> = MutableLiveData()
    val authData: LiveData<AuthData> = _authData
    val authValidity: MutableLiveData<Boolean> = MutableLiveData()
    private val _purchaseAppLiveData: MutableLiveData<FusedDownload> = MutableLiveData()
    val purchaseAppLiveData: LiveData<FusedDownload> = _purchaseAppLiveData
    val isAppPurchased: MutableLiveData<String> = MutableLiveData()
    val purchaseDeclined: MutableLiveData<String> = MutableLiveData()
    var authRequestRunning = false

    /*
     * Store the time when auth data is fetched for the first time.
     * If we try to fetch auth data after timeout, then don't allow it.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    var firstAuthDataFetchTime = 0L

    var isTokenValidationCompletedOnce = false

    // Downloads
    val downloadList = fusedManagerRepository.getDownloadLiveList()
    var installInProgress = false
    private val _errorMessage = MutableLiveData<Exception>()
    val errorMessage: LiveData<Exception> = _errorMessage

    private val _errorMessageStringResource = MutableLiveData<Int>()
    val errorMessageStringResource: LiveData<Int> = _errorMessageStringResource
    /*
     * Authentication related functions
     */

    companion object {
        private const val TAG = "MainActivityViewModel"
        private var isGoogleLoginRunning = false
    }

    private fun setFirstTokenFetchTime() {
        if (firstAuthDataFetchTime == 0L) {
            firstAuthDataFetchTime = SystemClock.uptimeMillis()
        }
    }

    private fun isTimeEligibleForTokenRefresh(): Boolean {
        return (SystemClock.uptimeMillis() - firstAuthDataFetchTime) <= timeoutDurationInMillis
    }

    /*
     * This method resets the last recorded token fetch time.
     * Then it posts authValidity as false. This causes the observer in MainActivity to destroyCredentials
     * and fetch new token.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5404
     */
    fun retryFetchingTokenAfterTimeout() {
        firstAuthDataFetchTime = 0
        setFirstTokenFetchTime()
        authValidity.postValue(false)
    }

    fun uploadFaultyTokenToEcloud(description: String) {
        viewModelScope.launch {
            authData.value?.let { authData ->
                val email: String = authData.run {
                    if (email != "null") email
                    else userProfile?.email ?: "null"
                }
                ecloudRepository.uploadFaultyEmail(email, description)
            }
        }
    }

    fun getAuthData() {
        if (!authRequestRunning) {
            authRequestRunning = true
            viewModelScope.launch {
                /*
                 * If getting auth data failed, try getting again.
                 * Sending false in authValidity, triggers observer in MainActivity,
                 * causing it to destroy credentials and try to regenerate auth data.
                 *
                 * Issue:
                 * https://gitlab.e.foundation/e/backlog/-/issues/5413
                 * https://gitlab.e.foundation/e/backlog/-/issues/5404
                 */
                if (!fusedAPIRepository.fetchAuthData()) {
                    authRequestRunning = false
                    authValidity.postValue(false)
                }
            }
        }
    }

    fun updateAuthData(authData: AuthData) {
        _authData.value = authData
    }

    fun destroyCredentials(regenerateFunction: ((user: String) -> Unit)?) {
        viewModelScope.launch {
            /*
             * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5168
             *
             * Now destroyCredentials() no longer removes the user type from data store.
             * (i.e. Google login or Anonymous).
             * - If the type is User.ANONYMOUS then we do not prompt the user to login again,
             *   we directly generate new auth data; which is the main Gitlab issue described above.
             * - If not anonymous user, i.e. type is User.GOOGLE, in that case we clear
             *   the USERTYPE value. This causes HomeFragment.onTosAccepted() to open
             *   SignInFragment as we need fresh login from the user.
             */
            dataStoreModule.destroyCredentials()
            if (regenerateFunction != null) {
                dataStoreModule.userType.collect { user ->
                    if (!user.isBlank() && User.valueOf(user) == User.ANONYMOUS) {
                        Log.d(TAG, "Regenerating auth data for Anonymous user")
                        regenerateFunction(user)
                    } else {
                        Log.d(TAG, "Ask Google user to log in again")
                        dataStoreModule.clearUserType()
                    }
                }
            }
        }
    }

    fun generateAuthData() {
        val data = jsonToAuthData()
        _authData.value = data
    }

    private fun jsonToAuthData() = gson.fromJson(authDataJson.value, AuthData::class.java)

    fun validateAuthData() {
        viewModelScope.launch {
            jsonToAuthData()?.let {
                val isAuthValid = isAuthValid(it)
                authValidity.postValue(isAuthValid)
                authRequestRunning = false
            }
        }
    }

    fun handleAuthDataJson() {
        val user = userType.value
        val json = authDataJson.value

        if (user == null || json == null) {
            return
        }

        if (!isUserLoggedIn(user, json)) {
            generateAuthDataBasedOnUserType(user)
        } else if (isEligibleToValidateJson(json) && internetConnection.value == true) {
            validateAuthData()
            Log.d(TAG, ">>> Authentication data is available!")
        }
    }

    private fun isUserLoggedIn(user: String, json: String) =
        user.isNotEmpty() && !user.contentEquals(User.UNAVAILABLE.name) && json.isNotEmpty()

    private fun isEligibleToValidateJson(authDataJson: String?) =
        !authDataJson.isNullOrEmpty() && !userType.value.isNullOrEmpty() && !userType.value.contentEquals(
            User.UNAVAILABLE.name
        )

    fun handleAuthValidity(isValid: Boolean, handleTimeoOut: () -> Unit) {
        if (isGoogleLoginRunning) {
            return
        }
        isTokenValidationCompletedOnce = true
        if (isValid) {
            Log.d(TAG, "Authentication data is valid!")
            generateAuthData()
            return
        }
        Log.d(TAG, ">>> Authentication data validation failed!")
        destroyCredentials { user ->
            if (isTimeEligibleForTokenRefresh()) {
                generateAuthDataBasedOnUserType(user)
            } else {
                handleTimeoOut()
            }
        }
    }

    private fun generateAuthDataBasedOnUserType(user: String) {
        if (user.isEmpty() || tocStatus.value == false || isGoogleLoginRunning) {
            return
        }
        when (User.valueOf(user)) {
            User.ANONYMOUS -> {
                if (authDataJson.value.isNullOrEmpty() && !authRequestRunning) {
                    Log.d(TAG, ">>> Fetching new authentication data")
                    setFirstTokenFetchTime()
                    getAuthData()
                }
            }
            User.UNAVAILABLE -> {
                destroyCredentials(null)
            }
            User.GOOGLE -> {
                if (authData.value == null && !authRequestRunning) {
                    Log.d(TAG, ">>> Fetching new authentication data")
                    setFirstTokenFetchTime()
                    doFetchAuthData()
                }
            }
        }
    }

    private suspend fun doFetchAuthData(email: String, oauthToken: String) {
        var responseMap: Map<String, String>
        withContext(Dispatchers.IO) {
            val response = aC2DMTask.getAC2DMResponse(email, oauthToken)
            responseMap = response
            responseMap["Token"]?.let {
                if (fusedAPIRepository.fetchAuthData(email, it) == null) {
                    dataStoreModule.clearUserType()
                    _errorMessageStringResource.value = R.string.unknown_error
                }
            }
        }
    }

    private fun doFetchAuthData() {
        viewModelScope.launch {
            isGoogleLoginRunning = true
            val email = dataStoreModule.getEmail()
            val oauthToken = dataStoreModule.getAASToken()
            if (email.isNotEmpty() && oauthToken.isNotEmpty()) {
                doFetchAuthData(email, oauthToken)
            }
            isGoogleLoginRunning = false
        }
    }

    private suspend fun isAuthValid(authData: AuthData): Boolean {
        return fusedAPIRepository.validateAuthData(authData)
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
        if (!app.isFree && authData.value?.isAnonymous == true) {
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
        if (!fusedApp.isFree && fusedApp.price.isBlank()) {
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

    fun getApplication(app: FusedApp, imageView: ImageView?) {
        if (shouldShowPaidAppsSnackBar(app)) {
            return
        }
        viewModelScope.launch {
            val fusedDownload: FusedDownload
            try {
                val appIcon = imageView?.let { getImageBase64(it) } ?: ""
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
                    appIcon,
                    app.latest_version_code,
                    app.offer_type,
                    app.isFree,
                    app.originalSize
                )
                updateFusedDownloadWithAppDownloadLink(app, fusedDownload)
            } catch (e: Exception) {
                if (e is ApiException.AppNotPurchased) {
                    handleAppNotPurchased(imageView, app)
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
        imageView: ImageView?,
        app: FusedApp
    ) {
        val appIcon = imageView?.let { getImageBase64(it) } ?: ""
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
            appIcon,
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
        authData.value?.let {
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
        authData.value?.let {
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

    private fun getImageBase64(imageView: ImageView): String {
        val byteArrayOS = ByteArrayOutputStream()
        val bitmap = imageView.drawable.toBitmap()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
        return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT)
    }

    val internetConnection = liveData {
        emitSource(ReactiveNetwork().observeInternetConnectivity().asLiveData(Dispatchers.Default))
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
}
