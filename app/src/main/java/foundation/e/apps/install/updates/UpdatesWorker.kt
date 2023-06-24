package foundation.e.apps.install.updates

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.login.LoginSourceRepository
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.data.updates.UpdatesManagerRepository
import foundation.e.apps.install.workmanager.InstallWorkManager
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class UpdatesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val updatesManagerRepository: UpdatesManagerRepository,
    private val fusedAPIRepository: FusedAPIRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val dataStoreManager: DataStoreManager,
    private val loginSourceRepository: LoginSourceRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val IS_AUTO_UPDATE = "IS_AUTO_UPDATE"
        private const val MAX_RETRY_COUNT = 10
        private const val DELAY_FOR_RETRY = 3000L
    }

    val TAG = UpdatesWorker::class.simpleName
    private var shouldShowNotification = true
    private var automaticInstallEnabled = true
    private var onlyOnUnmeteredNetwork = false
    private var isAutoUpdate = true // indicates it is auto update or user initiated update
    private var retryCount = 0

    override suspend fun doWork(): Result {
        return try {
            isAutoUpdate = params.inputData.getBoolean(IS_AUTO_UPDATE, true)
            if (isAutoUpdate && checkManualUpdateRunning()) {
                return Result.success()
            }

            checkForUpdates()
            Result.success()
        } catch (e: Throwable) {
            Timber.e(e)
            Result.failure()
        } finally {
            if (shouldShowNotification && automaticInstallEnabled) {
                UpdatesNotifier.cancelNotification(context)
            }
        }
    }

    private suspend fun checkManualUpdateRunning(): Boolean {
        val workInfos =
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).getWorkInfosByTag(UpdatesWorkManager.USER_TAG)
                    .get()
            }
        if (workInfos.isNotEmpty()) {
            val workInfo = workInfos[0]
            Timber.d("Manual update status: workInfo.state=${workInfo.state}, id=${workInfo.id}")
            return when (workInfo.state) {
                State.BLOCKED, State.ENQUEUED, State.RUNNING -> true
                else -> false
            }
        }
        return false
    }

    private fun getUser(): User {
        return dataStoreManager.getUserType()
    }

    private suspend fun checkForUpdates() {
        loadSettings()
        val isConnectedToUnmeteredNetwork = isConnectedToUnmeteredNetwork(applicationContext)
        val appsNeededToUpdate = mutableListOf<FusedApp>()
        val user = getUser()
        val authData = loginSourceRepository.getValidatedAuthData().data
        val resultStatus: ResultStatus

        if (user in listOf(User.ANONYMOUS, User.GOOGLE) && authData != null) {
            /*
             * Signifies valid Google user and valid auth data to update
             * apps from Google Play store.
             * The user check will be more useful in No-Google mode.
             */
            val updateData = updatesManagerRepository.getUpdates(authData)
            appsNeededToUpdate.addAll(updateData.first)
            resultStatus = updateData.second
        } else if (user != User.UNAVAILABLE) {
            /*
             * If authData is null, update apps from cleanapk only.
             */
            val updateData = updatesManagerRepository.getUpdatesOSS()
            appsNeededToUpdate.addAll(updateData.first)
            resultStatus = updateData.second
        } else {
            /*
             * If user in UNAVAILABLE, don't do anything.
             */
            Timber.w("User is not available! User is required during update!")
            return
        }
        Timber.i("Updates found: ${appsNeededToUpdate.size}; $resultStatus")
        if (isAutoUpdate && shouldShowNotification) {
            handleNotification(appsNeededToUpdate.size, isConnectedToUnmeteredNetwork)
        }

        if (resultStatus != ResultStatus.OK) {
            manageRetry()
        } else {
            /*
             * Show notification only if enabled.
             * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5376
             */
            retryCount = 0
            if (isAutoUpdate && shouldShowNotification) {
                handleNotification(appsNeededToUpdate.size, isConnectedToUnmeteredNetwork)
            }

            triggerUpdateProcessOnSettings(
                isConnectedToUnmeteredNetwork,
                appsNeededToUpdate,
                /*
                 * If authData is null, only cleanApk data will be present
                 * in appsNeededToUpdate list. Hence it is safe to proceed with
                 * blank AuthData.
                 */
                authData ?: AuthData("", ""),
            )
        }
    }

    private suspend fun manageRetry() {
        retryCount++
        if (retryCount == 1) {
            EventBus.invokeEvent(AppEvent.UpdateEvent(ResultSupreme.WorkError(ResultStatus.RETRY)))
        }

        if (retryCount <= MAX_RETRY_COUNT) {
            delay(DELAY_FOR_RETRY)
            checkForUpdates()
        } else {
            EventBus.invokeEvent(AppEvent.UpdateEvent(ResultSupreme.WorkError(ResultStatus.UNKNOWN)))
        }
    }

    private suspend fun triggerUpdateProcessOnSettings(
        isConnectedToUnmeteredNetwork: Boolean,
        appsNeededToUpdate: List<FusedApp>,
        authData: AuthData
    ) {
        if ((!isAutoUpdate || automaticInstallEnabled) &&
            applicationContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            if (onlyOnUnmeteredNetwork && isConnectedToUnmeteredNetwork) {
                startUpdateProcess(appsNeededToUpdate, authData)
            } else if (!onlyOnUnmeteredNetwork) {
                startUpdateProcess(appsNeededToUpdate, authData)
            }
        }
    }

    private fun handleNotification(
        numberOfAppsNeedUpdate: Int,
        isConnectedToUnmeteredNetwork: Boolean
    ) {
        if (numberOfAppsNeedUpdate > 0) {
            UpdatesNotifier.showNotification(
                applicationContext,
                numberOfAppsNeedUpdate,
                automaticInstallEnabled,
                onlyOnUnmeteredNetwork,
                isConnectedToUnmeteredNetwork
            )
        }
    }

    private suspend fun startUpdateProcess(
        appsNeededToUpdate: List<FusedApp>,
        authData: AuthData
    ) {
        appsNeededToUpdate.forEach { fusedApp ->
            if (!fusedApp.isFree && authData.isAnonymous) {
                return@forEach
            }

            val fusedDownload = FusedDownload(
                fusedApp._id,
                fusedApp.origin,
                fusedApp.status,
                fusedApp.name,
                fusedApp.package_name,
                mutableListOf(),
                mutableMapOf(),
                fusedApp.status,
                fusedApp.type,
                fusedApp.icon_image_path,
                fusedApp.latest_version_code,
                fusedApp.offer_type,
                fusedApp.isFree,
                fusedApp.originalSize
            )

            try {
                updateFusedDownloadWithAppDownloadLink(fusedApp, authData, fusedDownload)
            } catch (e: Exception) {
                Timber.e(e)
                EventBus.invokeEvent(
                    AppEvent.UpdateEvent(
                        ResultSupreme.WorkError(
                            ResultStatus.UNKNOWN,
                            fusedDownload
                        )
                    )
                )
                return@forEach
            }

            val isSuccess = fusedManagerRepository.addDownload(fusedDownload)
            if (!isSuccess) {
                Timber.i("Update adding ABORTED! status: $isSuccess")
                return@forEach
            }

            fusedManagerRepository.updateAwaiting(fusedDownload)
            InstallWorkManager.enqueueWork(fusedDownload, true)
            Timber.i("startUpdateProcess: Enqueued for update: ${fusedDownload.name} ${fusedDownload.id} ${fusedDownload.status}")
        }
    }

    private fun loadSettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        shouldShowNotification =
            preferences.getBoolean(
                applicationContext.getString(
                    R.string.updateNotify
                ),
                true
            )
        automaticInstallEnabled = preferences.getBoolean(
            applicationContext.getString(
                R.string.auto_install_enabled
            ),
            true
        )

        onlyOnUnmeteredNetwork = preferences.getBoolean(
            applicationContext.getString(
                R.string.only_unmetered_network
            ),
            false
        )
    }

    private suspend fun updateFusedDownloadWithAppDownloadLink(
        app: FusedApp,
        authData: AuthData,
        fusedDownload: FusedDownload
    ) {
        val downloadList = mutableListOf<String>()
        if (app.type == Type.PWA) {
            downloadList.add(app.url)
            fusedDownload.downloadURLList = downloadList
        } else {
            fusedAPIRepository.updateFusedDownloadWithDownloadingInfo(
                authData,
                app.origin,
                fusedDownload
            )
        }
    }

    /*
     * Checks if the device is connected to a metered connection or not
     * @param context current Context
     * @return returns true if the connections is not metered, false otherwise
     */
    private fun isConnectedToUnmeteredNetwork(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                return true
            }
        }
        return false
    }
}
