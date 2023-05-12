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

package foundation.e.apps.manager.workmanager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.api.fused.UpdatesDao
import foundation.e.apps.manager.database.DatabaseRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.updates.UpdatesNotifier
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.getFormattedString
import foundation.e.apps.utils.modules.DataStoreManager
import kotlinx.coroutines.flow.transformWhile
import timber.log.Timber
import java.text.NumberFormat
import java.util.Date
import javax.inject.Inject

class AppInstallProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseRepository: DatabaseRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val dataStoreManager: DataStoreManager
) {

    private var isItUpdateWork = false

    companion object {
        private const val TAG = "AppInstallProcessor"
        private const val DATE_FORMAT = "dd/MM/yyyy-HH:mm"
    }

    suspend fun processInstall(
        fusedDownloadId: String,
        isItUpdateWork: Boolean,
        runInForeground: (suspend (String) -> Unit)? = null
    ): Result<ResultStatus> {
        var fusedDownload: FusedDownload? = null
        try {
            Timber.d("Fused download name $fusedDownloadId")

            fusedDownload = databaseRepository.getDownloadById(fusedDownloadId)
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
            Timber.e("doWork: Failed: ${e.stackTraceToString()}")
            fusedDownload?.let {
                fusedManagerRepository.cancelDownload(fusedDownload)
            }
        }

        Timber.i("doWork: RESULT SUCCESS: ${fusedDownload?.name}")
        return Result.success(ResultStatus.OK)
    }

    private fun areFilesDownloadedButNotInstalled(fusedDownload: FusedDownload) =
        fusedDownload.areFilesDownloaded() && (
            !fusedManagerRepository.isFusedDownloadInstalled(
                fusedDownload
            ) || fusedDownload.status == Status.INSTALLING
            )

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
        val downloadListWithoutAnyIssue =
            databaseRepository.getDownloadList()
                .filter {
                    !listOf(
                        Status.INSTALLATION_ISSUE,
                        Status.PURCHASE_NEEDED
                    ).contains(it.status)
                }

        return UpdatesDao.successfulUpdatedApps.isNotEmpty() && downloadListWithoutAnyIssue.isEmpty()
    }

    private fun showNotificationOnUpdateEnded() {
        val locale = dataStoreManager.getAuthData().locale
        val date = Date().getFormattedString(DATE_FORMAT, locale)
        val numberOfUpdatedApps = NumberFormat.getNumberInstance(locale)
            .format(UpdatesDao.successfulUpdatedApps.size)
            .toString()

        UpdatesNotifier.showNotification(
            context, context.getString(R.string.update),
            context.getString(
                R.string.message_last_update_triggered, numberOfUpdatedApps, date
            )
        )
    }

    private suspend fun startAppInstallationProcess(fusedDownload: FusedDownload) {
        if (fusedDownload.isAwaiting()) {
            fusedManagerRepository.downloadApp(fusedDownload)
            Timber.i("===> doWork: Download started ${fusedDownload.name} ${fusedDownload.status}")
        }

        databaseRepository.getDownloadFlowById(fusedDownload.id)
            .transformWhile {
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
            Timber.e(TAG, "observeDownload: ", e)
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
