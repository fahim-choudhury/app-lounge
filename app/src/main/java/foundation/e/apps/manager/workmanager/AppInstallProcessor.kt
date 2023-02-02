/*
 * Copyright ECORP SAS 2022
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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.api.DownloadManager
import foundation.e.apps.api.fused.UpdatesDao
import foundation.e.apps.manager.database.DatabaseRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.updates.UpdatesNotifier
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.modules.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AppInstallProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseRepository: DatabaseRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val downloadManager: DownloadManager,
    private val dataStoreManager: DataStoreManager
) {

    private var isDownloading: Boolean = false
    private var isItUpdateWork = false

    companion object {
        private const val TAG = "AppInstallProcessor"
    }

    private val mutex = Mutex(true)

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

                if (fusedDownload.areFilesDownloaded() && !fusedManagerRepository.isFusedDownloadInstalled(
                        fusedDownload
                    )
                ) {
                    Timber.i("===> Downloaded But not installed ${fusedDownload.name}")
                    fusedManagerRepository.updateDownloadStatus(fusedDownload, Status.INSTALLING)
                }

                runInForeground?.invoke(it.name)

                if (!fusedManagerRepository.validateFusedDownload(fusedDownload)) {
                    fusedManagerRepository.installationIssue(it)
                    Timber.d("!!! installationIssue")
                    return@let
                }

                startAppInstallationProcess(it)
                mutex.lock()
            }
        } catch (e: Exception) {
            Timber.e("doWork: Failed: ${e.stackTraceToString()}")
            fusedDownload?.let {
                fusedManagerRepository.installationIssue(it)
            }
        }

        Timber.i("doWork: RESULT SUCCESS: ${fusedDownload?.name}")
        return Result.success(ResultStatus.OK)
    }

    private suspend fun checkUpdateWork(
        fusedDownload: FusedDownload?
    ) {
        if (isItUpdateWork) {
            fusedDownload?.let {
                val packageStatus = fusedManagerRepository.getFusedDownloadPackageStatus(fusedDownload)

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
        val date = Date(System.currentTimeMillis())
        val locale = dataStoreManager.getAuthData().locale
        val dateFormat =
            SimpleDateFormat("dd/MM/yyyy-HH:mm", locale)
        val numberOfUpdatedApps = NumberFormat.getNumberInstance(locale)
            .format(UpdatesDao.successfulUpdatedApps.size)
            .toString()

        UpdatesNotifier.showNotification(
            context, context.getString(R.string.update),
            context.getString(
                R.string.message_last_update_triggered, numberOfUpdatedApps, dateFormat.format(date)
            )
        )
    }

    private suspend fun startAppInstallationProcess(
        fusedDownload: FusedDownload
    ): Boolean {
        if (fusedDownload.isAwaiting()) {
            fusedManagerRepository.downloadApp(fusedDownload)
            Timber.i("===> doWork: Download started ${fusedDownload.name} ${fusedDownload.status}")
        }

        isDownloading = true
        /**
         * observe app download/install process in a separate thread as DownloadManager download artifacts in a separate process
         * It checks install status every three seconds
         */
        tickerFlow(3.seconds)
            .onEach {
                val download = databaseRepository.getDownloadById(fusedDownload.id)
                if (download == null) {
                    finishInstallation(fusedDownload)
                } else {
                    handleFusedDownloadStatusCheckingException(download)
                    if (isAppDownloading(download)) {
                        checkDownloadProcess(download)
                    }
                }
            }.launchIn(CoroutineScope(Dispatchers.IO))
        Timber.d(">>> ===> doWork: Download started " + fusedDownload.name + " " + fusedDownload.status)
        return true
    }

    private fun isAppDownloading(download: FusedDownload): Boolean {
        return download.type == Type.NATIVE && download.status != Status.INSTALLED && download.status != Status.INSTALLATION_ISSUE
    }

    private suspend fun handleFusedDownloadStatusCheckingException(
        download: FusedDownload
    ) {
        try {
            handleFusedDownloadStatus(download)
        } catch (e: Exception) {
            Log.e(TAG, "observeDownload: ", e)
            finishInstallation(download)
        }
    }

    /**
     * Triggers a repetitive event according to the delay passed in the parameter
     * @param period delay of each event
     * @param initialDelay initial delay to trigger the first event
     */
    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (isDownloading) {
            emit(Unit)
            delay(period)
        }
    }

    private suspend fun checkDownloadProcess(fusedDownload: FusedDownload) {
        downloadManager.checkDownloadProcess(fusedDownload.downloadIdMap.keys.toLongArray()) {
            fusedManagerRepository.installationIssue(fusedDownload)
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
                finishInstallation(fusedDownload)
                Timber.i("===> doWork: Installed/Failed: ${fusedDownload.name} ${fusedDownload.status}")
            }
            else -> {
                finishInstallation(fusedDownload)
                Timber.wtf(
                    TAG,
                    "===> ${fusedDownload.name} is in wrong state ${fusedDownload.status}"
                )
            }
        }
    }

    private suspend fun finishInstallation(fusedDownload: FusedDownload) {
        checkUpdateWork(fusedDownload)
        isDownloading = false
        unlockMutex()
    }

    private fun unlockMutex() {
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }
}
