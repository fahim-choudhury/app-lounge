package foundation.e.apps.updates.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import foundation.e.apps.R
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.manager.workmanager.InstallWorkManager
import foundation.e.apps.updates.UpdatesNotifier
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URL

@HiltWorker
class UpdatesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val updatesManagerRepository: UpdatesManagerRepository,
    private val fusedAPIRepository: FusedAPIRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val dataStoreModule: DataStoreModule,
    private val gson: Gson,
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
            checkForUpdates()
            Result.success()
        } catch (e: Throwable) {
            Result.failure()
        } finally {
            if (shouldShowNotification && automaticInstallEnabled) {
                UpdatesNotifier.cancelNotification(context)
            }
        }
    }

    private suspend fun checkForUpdates() {
        loadSettings()
        val isConnectedToUnmeteredNetwork = isConnectedToUnmeteredNetwork(applicationContext)
        val authData = getAuthData()
        var resultStatus: ResultStatus

        val updateData = updatesManagerRepository.getUpdates(authData)
        val appsNeededToUpdate = updateData.first
        resultStatus = updateData.second

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

    private fun getAuthData(): AuthData {
        val authDataJson = dataStoreModule.getAuthDataSync()
        return gson.fromJson(authDataJson, AuthData::class.java)
    }

    private suspend fun startUpdateProcess(
        appsNeededToUpdate: List<FusedApp>,
        authData: AuthData
    ) {
        appsNeededToUpdate.forEach { fusedApp ->
            if (!fusedApp.isFree && authData.isAnonymous) {
                return@forEach
            }
            val iconBase64 = getIconImageToBase64(fusedApp)

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
                iconBase64,
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

            fusedManagerRepository.addDownload(fusedDownload)
            fusedManagerRepository.updateAwaiting(fusedDownload)
            Timber.d("startUpdateProcess: Enqueued for update: ${fusedDownload.name} ${fusedDownload.id} ${fusedDownload.status}")
            InstallWorkManager.enqueueWork(fusedDownload)
            Timber.d(">>> startUpdateProcess: " + fusedDownload.name)
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

    private fun getIconImageToBase64(fusedApp: FusedApp): String {
        val url =
            if (fusedApp.origin == Origin.CLEANAPK) "${CleanAPKInterface.ASSET_URL}${fusedApp.icon_image_path}" else fusedApp.icon_image_path
        val stream = URL(url).openStream()
        val bitmap = BitmapFactory.decodeStream(stream)
        val byteArrayOS = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOS)
        return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT)
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
