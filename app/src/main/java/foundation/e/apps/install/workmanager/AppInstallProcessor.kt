/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.install.workmanager

import android.content.Context
import com.aurora.gplayapi.exceptions.ApiException
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.UpdatesDao
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.install.AppInstallRepository
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.playstore.utils.GplayHttpRequestException
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.CheckAppAgeLimitUseCase
import foundation.e.apps.install.download.DownloadManagerUtils
import foundation.e.apps.install.notification.StorageNotificationManager
import foundation.e.apps.install.updates.UpdatesNotifier
import foundation.e.apps.utils.StorageComputer
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.getFormattedString
import foundation.e.apps.utils.isNetworkAvailable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.transformWhile
import timber.log.Timber
import java.text.NumberFormat
import java.util.Date
import javax.inject.Inject

class AppInstallProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInstallRepository: AppInstallRepository,
    private val appManagerWrapper: AppManagerWrapper,
    private val applicationRepository: ApplicationRepository,
    private val checkAppAgeLimitUseCase: CheckAppAgeLimitUseCase,
    private val dataStoreManager: DataStoreManager,
    private val storageNotificationManager: StorageNotificationManager,
) {

    @Inject
    lateinit var downloadManager: DownloadManagerUtils

    private var isItUpdateWork = false

    companion object {
        private const val TAG = "AppInstallProcessor"
        private const val DATE_FORMAT = "dd/MM/yyyy-HH:mm"
    }

    /**
     * creates [AppInstall] from [Application] and enqueues into WorkManager to run install process.
     * @param application represents the app info which will be installed
     * @param isAnUpdate indicates the app is requested for update or not
     *
     */
    suspend fun initAppInstall(
        application: Application,
        isAnUpdate: Boolean = false
    ) {
        val appInstall = AppInstall(
            application._id,
            application.origin,
            application.status,
            application.name,
            application.package_name,
            mutableListOf(),
            mutableMapOf(),
            application.status,
            application.type,
            application.icon_image_path,
            application.latest_version_code,
            application.offer_type,
            application.isFree,
            application.originalSize
        )

        appInstall.contentRating = application.contentRating

        if (appInstall.type == Type.PWA) {
            appInstall.downloadURLList = mutableListOf(application.url)
        }

        enqueueFusedDownload(appInstall, isAnUpdate)
    }

    /**
     * Enqueues [AppInstall] into WorkManager to run app install process. Before enqueuing,
     * It validates some corner cases
     * @param appInstall represents the app downloading and installing related info, example- Installing Status,
     * Url of the APK,OBB files are needed to be downloaded and installed etc.
     * @param isAnUpdate indicates the app is requested for update or not
     */
    suspend fun enqueueFusedDownload(
        appInstall: AppInstall,
        isAnUpdate: Boolean = false
    ) {
        try {
            val authData = dataStoreManager.getAuthData()

            if (!appInstall.isFree && authData.isAnonymous) {
                EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.paid_app_anonymous_message))
                return
            }

            if (appInstall.type != Type.PWA && !updateDownloadUrls(appInstall)) return

            val downloadAdded = appManagerWrapper.addDownload(appInstall)
            if (!downloadAdded) {
                Timber.i("Update adding ABORTED! status: $downloadAdded")
                return
            }

            if (checkAppAgeLimitUseCase.invoke(appInstall)) {
                Timber.i("Content rating is not allowed for: ${appInstall.name}")
                EventBus.invokeEvent(AppEvent.AgeLimitRestrictionEvent(appInstall.name))
                appManagerWrapper.cancelDownload(appInstall)
                return
            }

            if (!context.isNetworkAvailable()) {
                appManagerWrapper.installationIssue(appInstall)
                EventBus.invokeEvent(AppEvent.NoInternetEvent(false))
                return
            }

            if (StorageComputer.spaceMissing(appInstall) > 0) {
                Timber.d("Storage is not available for: ${appInstall.name} size: ${appInstall.appSize}")
                storageNotificationManager.showNotEnoughSpaceNotification(appInstall)
                appManagerWrapper.installationIssue(appInstall)
                EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.not_enough_storage))
                return
            }

            appManagerWrapper.updateAwaiting(appInstall)
            InstallWorkManager.enqueueWork(appInstall, isAnUpdate)
        } catch (e: Exception) {
            Timber.e(
                "Enqueuing App install work is failed for ${appInstall.packageName} exception: ${e.localizedMessage}",
                e
            )
            appManagerWrapper.installationIssue(appInstall)
        }
    }

    // returns TRUE if updating urls is successful, otherwise false.
    private suspend fun updateDownloadUrls(appInstall: AppInstall): Boolean {
        try {
            updateFusedDownloadWithAppDownloadLink(appInstall)
        } catch (e: ApiException.AppNotPurchased) {
            appManagerWrapper.addFusedDownloadPurchaseNeeded(appInstall)
            EventBus.invokeEvent(AppEvent.AppPurchaseEvent(appInstall))
            return false
        } catch (e: GplayHttpRequestException) {
            handleUpdateDownloadError(
                appInstall,
                "${appInstall.packageName} code: ${e.status} exception: ${e.localizedMessage}",
                e
            )
            return false
        } catch (e: Exception) {
            handleUpdateDownloadError(
                appInstall,
                "${appInstall.packageName} exception: ${e.localizedMessage}",
                e
            )
            return false
        }
        return true
    }

    private suspend fun handleUpdateDownloadError(
        appInstall: AppInstall,
        message: String,
        e: Exception
    ) {
        Timber.e("Updating download Urls failed for $message", e)
        EventBus.invokeEvent(
            AppEvent.UpdateEvent(
                ResultSupreme.WorkError(
                    ResultStatus.UNKNOWN, appInstall
                )
            )
        )
    }

    private suspend fun updateFusedDownloadWithAppDownloadLink(
        appInstall: AppInstall
    ) {
        applicationRepository.updateFusedDownloadWithDownloadingInfo(
            appInstall.origin, appInstall
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun processInstall(
        fusedDownloadId: String,
        isItUpdateWork: Boolean,
        runInForeground: (suspend (String) -> Unit)? = null
    ): Result<ResultStatus> {
        var appInstall: AppInstall? = null
        try {
            Timber.d("Fused download name $fusedDownloadId")

            appInstall = appInstallRepository.getDownloadById(fusedDownloadId)
            Timber.i(">>> dowork started for Fused download name " + appInstall?.name + " " + fusedDownloadId)

            appInstall?.let {

                checkDownloadingState(appInstall)

                this.isItUpdateWork =
                    isItUpdateWork && appManagerWrapper.isFusedDownloadInstalled(appInstall)

                if (!appInstall.isAppInstalling()) {
                    Timber.d("!!! returned")
                    return@let
                }

                if (!appManagerWrapper.validateFusedDownload(appInstall)) {
                    appManagerWrapper.installationIssue(it)
                    Timber.d("!!! installationIssue")
                    return@let
                }

                if (areFilesDownloadedButNotInstalled(appInstall)) {
                    Timber.i("===> Downloaded But not installed ${appInstall.name}")
                    appManagerWrapper.updateDownloadStatus(appInstall, Status.INSTALLING)
                }

                runInForeground?.invoke(it.name)

                startAppInstallationProcess(it)
            }
        } catch (e: Exception) {
            Timber.e(
                "Install worker is failed for ${appInstall?.packageName} exception: ${e.localizedMessage}",
                e
            )
            appInstall?.let {
                appManagerWrapper.cancelDownload(appInstall)
            }
        }

        Timber.i("doWork: RESULT SUCCESS: ${appInstall?.name}")
        return Result.success(ResultStatus.OK)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun checkDownloadingState(appInstall: AppInstall) {
        if (appInstall.status == Status.DOWNLOADING) {
            appInstall.downloadIdMap.keys.forEach { downloadId ->
                downloadManager.updateDownloadStatus(downloadId)
            }
        }
    }

    private fun areFilesDownloadedButNotInstalled(appInstall: AppInstall) =
        appInstall.areFilesDownloaded() && (!appManagerWrapper.isFusedDownloadInstalled(
            appInstall
        ) || appInstall.status == Status.INSTALLING)

    private suspend fun checkUpdateWork(
        appInstall: AppInstall?
    ) {
        if (isItUpdateWork) {
            appInstall?.let {
                val packageStatus =
                    appManagerWrapper.getFusedDownloadPackageStatus(appInstall)

                if (packageStatus == Status.INSTALLED) {
                    UpdatesDao.addSuccessfullyUpdatedApp(it)
                }

                if (isUpdateCompleted()) { // show notification for ended update
                    showNotificationOnUpdateEnded()
                    UpdatesDao.clearSuccessfullyUpdatedApps()
                }
            }
        }
    }

    private suspend fun isUpdateCompleted(): Boolean {
        val downloadListWithoutAnyIssue = appInstallRepository.getDownloadList().filter {
            !listOf(
                Status.INSTALLATION_ISSUE, Status.PURCHASE_NEEDED
            ).contains(it.status)
        }

        return UpdatesDao.successfulUpdatedApps.isNotEmpty() && downloadListWithoutAnyIssue.isEmpty()
    }

    private fun showNotificationOnUpdateEnded() {
        val locale = dataStoreManager.getAuthData().locale
        val date = Date().getFormattedString(DATE_FORMAT, locale)
        val numberOfUpdatedApps =
            NumberFormat.getNumberInstance(locale).format(UpdatesDao.successfulUpdatedApps.size)
                .toString()

        UpdatesNotifier.showNotification(
            context, context.getString(R.string.update),
            context.getString(
                R.string.message_last_update_triggered, numberOfUpdatedApps, date
            )
        )
    }

    private suspend fun startAppInstallationProcess(appInstall: AppInstall) {
        if (appInstall.isAwaiting()) {
            appManagerWrapper.downloadApp(appInstall)
            Timber.i("===> doWork: Download started ${appInstall.name} ${appInstall.status}")
        }

        appInstallRepository.getDownloadFlowById(appInstall.id).transformWhile {
            emit(it)
            isInstallRunning(it)
        }.collect { latestFusedDownload ->
            handleFusedDownload(latestFusedDownload, appInstall)
        }
    }

    /**
     * Takes actions depending on the status of [AppInstall]
     *
     * @param latestAppInstall comes from Room database when [Status] is updated
     * @param appInstall is the original object when install process isn't started. It's used when [latestAppInstall]
     * becomes null, After installation is completed.
     */
    private suspend fun handleFusedDownload(
        latestAppInstall: AppInstall?,
        appInstall: AppInstall
    ) {
        if (latestAppInstall == null) {
            Timber.d("===> download null: finish installation")
            finishInstallation(appInstall)
            return
        }

        handleFusedDownloadStatusCheckingException(latestAppInstall)
    }

    private fun isInstallRunning(it: AppInstall?) =
        it != null && it.status != Status.INSTALLATION_ISSUE

    private suspend fun handleFusedDownloadStatusCheckingException(
        download: AppInstall
    ) {
        try {
            handleFusedDownloadStatus(download)
        } catch (e: Exception) {
            val message =
                "Handling install status is failed for ${download.packageName} exception: ${e.localizedMessage}"
            Timber.e(message, e)
            appManagerWrapper.installationIssue(download)
            finishInstallation(download)
        }
    }

    private suspend fun handleFusedDownloadStatus(appInstall: AppInstall) {
        when (appInstall.status) {
            Status.AWAITING, Status.DOWNLOADING -> {
            }

            Status.DOWNLOADED -> {
                appManagerWrapper.updateDownloadStatus(appInstall, Status.INSTALLING)
            }

            Status.INSTALLING -> {
                Timber.i("===> doWork: Installing ${appInstall.name} ${appInstall.status}")
            }

            Status.INSTALLED, Status.INSTALLATION_ISSUE -> {
                Timber.i("===> doWork: Installed/Failed: ${appInstall.name} ${appInstall.status}")
                finishInstallation(appInstall)
            }

            else -> {
                Timber.wtf(
                    TAG, "===> ${appInstall.name} is in wrong state ${appInstall.status}"
                )
                finishInstallation(appInstall)
            }
        }
    }

    private suspend fun finishInstallation(appInstall: AppInstall) {
        checkUpdateWork(appInstall)
    }
}
