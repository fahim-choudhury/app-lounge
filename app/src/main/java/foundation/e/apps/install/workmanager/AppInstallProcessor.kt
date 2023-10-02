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
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.UpdatesDao
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fusedDownload.FusedDownloadRepository
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.domain.install.usecase.AppInstallerUseCase
import foundation.e.apps.install.notification.StorageNotificationManager
import foundation.e.apps.install.updates.UpdatesNotifier
import foundation.e.apps.utils.StorageComputer
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.getFormattedString
import foundation.e.apps.utils.isNetworkAvailable
import kotlinx.coroutines.flow.transformWhile
import timber.log.Timber
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class AppInstallProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedDownloadRepository: FusedDownloadRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val fusedAPIRepository: FusedAPIRepository,
    private val appInstallerUseCase: AppInstallerUseCase,
    private val storageNotificationManager: StorageNotificationManager
) {

    private var isItUpdateWork = false

    companion object {
        private const val TAG = "AppInstallProcessor"
        private const val DATE_FORMAT = "dd/MM/yyyy-HH:mm"
    }

    /**
     * creates [FusedDownload] from [FusedApp] and enqueues into WorkManager to run install process.
     * @param fusedApp represents the app info which will be installed
     * @param isAnUpdate indicates the app is requested for update or not
     *
     */
    suspend fun initAppInstall(
        fusedApp: FusedApp,
        isAnUpdate: Boolean = false
    ) {
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

        if (fusedDownload.type == Type.PWA) {
            fusedDownload.downloadURLList = mutableListOf(fusedApp.url)
        }

        enqueueFusedDownload(fusedDownload, isAnUpdate)
    }

    /**
     * Enqueues [FusedDownload] into WorkManager to run app install process. Before enqueuing,
     * It validates some corner cases
     * @param fusedDownload represents the app downloading and installing related info, example- Installing Status,
     * Url of the APK,OBB files are needed to be downloaded and installed etc.
     * @param isAnUpdate indicates the app is requested for update or not
     */
    suspend fun enqueueFusedDownload(
        fusedDownload: FusedDownload,
        isAnUpdate: Boolean = false
    ) {
        try {
            val authData = appInstallerUseCase.currentAuthData()
            if (!fusedDownload.isFree && authData?.isAnonymous == true) {
                EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.paid_app_anonymous_message))
                return
            }

            if (fusedDownload.type != Type.PWA && !updateDownloadUrls(fusedDownload)) return

            val downloadAdded = fusedManagerRepository.addDownload(fusedDownload)
            if (!downloadAdded) {
                Timber.i("Update adding ABORTED! status: $downloadAdded")
                return
            }

            if (!context.isNetworkAvailable()) {
                fusedManagerRepository.installationIssue(fusedDownload)
                EventBus.invokeEvent(AppEvent.NoInternetEvent(false))
                return
            }

            if (StorageComputer.spaceMissing(fusedDownload) > 0) {
                Timber.d("Storage is not available for: ${fusedDownload.name} size: ${fusedDownload.appSize}")
                storageNotificationManager.showNotEnoughSpaceNotification(fusedDownload)
                fusedManagerRepository.installationIssue(fusedDownload)
                EventBus.invokeEvent(AppEvent.ErrorMessageEvent(R.string.not_enough_storage))
                return
            }

            fusedManagerRepository.updateAwaiting(fusedDownload)
            InstallWorkManager.enqueueWork(fusedDownload, isAnUpdate)
        } catch (e: Exception) {
            Timber.e(
                "Enqueuing App install work is failed for ${fusedDownload.packageName} exception: ${e.localizedMessage}",
                e
            )
            fusedManagerRepository.installationIssue(fusedDownload)
        }
    }

    // returns TRUE if updating urls is successful, otherwise false.
    private suspend fun updateDownloadUrls(fusedDownload: FusedDownload): Boolean {
        try {
            updateFusedDownloadWithAppDownloadLink(fusedDownload)
        } catch (e: ApiException.AppNotPurchased) {
            fusedManagerRepository.addFusedDownloadPurchaseNeeded(fusedDownload)
            EventBus.invokeEvent(AppEvent.AppPurchaseEvent(fusedDownload))
            return false
        } catch (e: Exception) {
            Timber.e(
                "Updating download Urls failed for ${fusedDownload.packageName} exception: ${e.localizedMessage}",
                e
            )
            EventBus.invokeEvent(
                AppEvent.UpdateEvent(
                    ResultSupreme.WorkError(
                        ResultStatus.UNKNOWN,
                        fusedDownload
                    )
                )
            )
            return false
        }
        return true
    }

    private suspend fun updateFusedDownloadWithAppDownloadLink(
        fusedDownload: FusedDownload
    ) {
        fusedAPIRepository.updateFusedDownloadWithDownloadingInfo(
            fusedDownload.origin,
            fusedDownload
        )
    }

    suspend fun processInstall(
        fusedDownloadId: String,
        isItUpdateWork: Boolean,
        runInForeground: (suspend (String) -> Unit)? = null
    ): Result<ResultStatus> {
        var fusedDownload: FusedDownload? = null
        try {
            Timber.d("Fused download name $fusedDownloadId")

            fusedDownload = fusedDownloadRepository.getDownloadById(fusedDownloadId)
            Timber.i(">>> dowork started for Fused download name " + fusedDownload?.name + " " + fusedDownloadId)

            fusedDownload?.let {
                this.isItUpdateWork = isItUpdateWork &&
                    fusedManagerRepository.isFusedDownloadInstalled(fusedDownload)

                if (!fusedDownload.isAppInstalling()) {
                    Timber.d("!!! returned")
                    return@let
                }

                if (!fusedManagerRepository.validateFusedDownload(fusedDownload)) {
                    fusedManagerRepository.installationIssue(it)
                    Timber.d("!!! installationIssue")
                    return@let
                }

                if (areFilesDownloadedButNotInstalled(fusedDownload)) {
                    Timber.i("===> Downloaded But not installed ${fusedDownload.name}")
                    fusedManagerRepository.updateDownloadStatus(fusedDownload, Status.INSTALLING)
                }

                runInForeground?.invoke(it.name)

                startAppInstallationProcess(it)
            }
        } catch (e: Exception) {
            Timber.e(
                "Install worker is failed for ${fusedDownload?.packageName} exception: ${e.localizedMessage}",
                e
            )
            fusedDownload?.let {
                fusedManagerRepository.cancelDownload(fusedDownload)
            }
        }

        Timber.i("doWork: RESULT SUCCESS: ${fusedDownload?.name}")
        return Result.success(ResultStatus.OK)
    }

    private fun areFilesDownloadedButNotInstalled(fusedDownload: FusedDownload) =
        fusedDownload.areFilesDownloaded() && (!fusedManagerRepository.isFusedDownloadInstalled(fusedDownload) || fusedDownload.status == Status.INSTALLING)

    private suspend fun checkUpdateWork(
        fusedDownload: FusedDownload?
    ) {
        if (isItUpdateWork) {
            fusedDownload?.let {
                val packageStatus =
                    fusedManagerRepository.getFusedDownloadPackageStatus(fusedDownload)

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
        val downloadListWithoutAnyIssue = fusedDownloadRepository.getDownloadList().filter {
            !listOf(
                Status.INSTALLATION_ISSUE,
                Status.PURCHASE_NEEDED
            ).contains(it.status)
        }

        return UpdatesDao.successfulUpdatedApps.isNotEmpty() && downloadListWithoutAnyIssue.isEmpty()
    }

    private fun showNotificationOnUpdateEnded() {
        val locale = appInstallerUseCase.currentAuthData()?.locale ?: Locale.getDefault()
        val date = Date().getFormattedString(DATE_FORMAT, locale)
        val numberOfUpdatedApps =
            NumberFormat.getNumberInstance(locale).format(UpdatesDao.successfulUpdatedApps.size)
                .toString()

        UpdatesNotifier.showNotification(
            context,
            context.getString(R.string.update),
            context.getString(
                R.string.message_last_update_triggered,
                numberOfUpdatedApps,
                date
            )
        )
    }

    private suspend fun startAppInstallationProcess(fusedDownload: FusedDownload) {
        if (fusedDownload.isAwaiting()) {
            fusedManagerRepository.downloadApp(fusedDownload)
            Timber.i("===> doWork: Download started ${fusedDownload.name} ${fusedDownload.status}")
        }

        fusedDownloadRepository.getDownloadFlowById(fusedDownload.id).transformWhile {
            emit(it)
            isInstallRunning(it)
        }.collect { latestFusedDownload ->
            handleFusedDownload(latestFusedDownload, fusedDownload)
        }
    }

    /**
     * Takes actions depending on the status of [FusedDownload]
     *
     * @param latestFusedDownload comes from Room database when [Status] is updated
     * @param fusedDownload is the original object when install process isn't started. It's used when [latestFusedDownload]
     * becomes null, After installation is completed.
     */
    private suspend fun handleFusedDownload(
        latestFusedDownload: FusedDownload?,
        fusedDownload: FusedDownload
    ) {
        if (latestFusedDownload == null) {
            Timber.d("===> download null: finish installation")
            finishInstallation(fusedDownload)
            return
        }

        handleFusedDownloadStatusCheckingException(latestFusedDownload)
    }

    private fun isInstallRunning(it: FusedDownload?) =
        it != null && it.status != Status.INSTALLATION_ISSUE

    private suspend fun handleFusedDownloadStatusCheckingException(
        download: FusedDownload
    ) {
        try {
            handleFusedDownloadStatus(download)
        } catch (e: Exception) {
            val message =
                "Handling install status is failed for ${download.packageName} exception: ${e.localizedMessage}"
            Timber.e(message, e)
            fusedManagerRepository.installationIssue(download)
            finishInstallation(download)
        }
    }

    private suspend fun handleFusedDownloadStatus(fusedDownload: FusedDownload) {
        when (fusedDownload.status) {
            Status.AWAITING, Status.DOWNLOADING -> {
            }

            Status.DOWNLOADED -> {
                fusedManagerRepository.updateDownloadStatus(fusedDownload, Status.INSTALLING)
            }

            Status.INSTALLING -> {
                Timber.i("===> doWork: Installing ${fusedDownload.name} ${fusedDownload.status}")
            }

            Status.INSTALLED, Status.INSTALLATION_ISSUE -> {
                Timber.i("===> doWork: Installed/Failed: ${fusedDownload.name} ${fusedDownload.status}")
                finishInstallation(fusedDownload)
            }

            else -> {
                Timber.wtf(
                    TAG,
                    "===> ${fusedDownload.name} is in wrong state ${fusedDownload.status}"
                )
                finishInstallation(fusedDownload)
            }
        }
    }

    private suspend fun finishInstallation(fusedDownload: FusedDownload) {
        checkUpdateWork(fusedDownload)
    }
}
