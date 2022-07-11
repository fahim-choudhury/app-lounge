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

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import foundation.e.apps.R
import foundation.e.apps.manager.database.DatabaseRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@HiltWorker
class InstallAppWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val databaseRepository: DatabaseRepository,
    private val fusedManagerRepository: FusedManagerRepository,
    private val downloadManager: DownloadManager,
    private val downloadManagerQuery: DownloadManager.Query,
) : CoroutineWorker(context, params) {

    private var isDownloading: Boolean = false

    companion object {
        private const val TAG = "InstallWorker"
        const val INPUT_DATA_FUSED_DOWNLOAD = "input_data_fused_download"

        /*
         * If this is not "static" then each notification has the same ID.
         * Making it static makes sure the id keeps increasing when atomicInteger.getAndIncrement()
         * is called.
         * This is possible cause for "Installing ..." notification to linger around
         * after the app is installed.
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5330
         */
        private val atomicInteger = AtomicInteger(100)
    }

    private val mutex = Mutex(true)

    override suspend fun doWork(): Result {
        var fusedDownload: FusedDownload? = null
        try {
            val fusedDownloadString = params.inputData.getString(INPUT_DATA_FUSED_DOWNLOAD) ?: ""
            Timber.d("Fused download name $fusedDownloadString")

            fusedDownload = databaseRepository.getDownloadById(fusedDownloadString)
            Timber.d(">>> dowork started for Fused download name " + fusedDownload?.name + " " + fusedDownloadString)
            fusedDownload?.let {
                if (fusedDownload.status != Status.AWAITING) {
                    return Result.success()
                }
                setForeground(
                    createForegroundInfo(
                        "Installing ${it.name}"
                    )
                )
                startAppInstallationProcess(it)
                mutex.lock()
            }
        } catch (e: Exception) {
            Timber.e("doWork: Failed: ${e.stackTraceToString()}")
            fusedDownload?.let {
                fusedManagerRepository.installationIssue(it)
            }
        } finally {
            Timber.d("doWork: RESULT SUCCESS: ${fusedDownload?.name}")
            return Result.success()
        }
    }

    private suspend fun startAppInstallationProcess(
        fusedDownload: FusedDownload
    ) {
        fusedManagerRepository.downloadApp(fusedDownload)
        Timber.d("===> doWork: Download started ${fusedDownload.name} ${fusedDownload.status}")
        isDownloading = true
        tickerFlow(3.seconds)
            .onEach {
                val download = databaseRepository.getDownloadById(fusedDownload.id)
                if (download == null) {
                    isDownloading = false
                    unlockMutex()
                } else {
                    handleFusedDownloadStatusCheckingException(download)
                    if (isAppDownloading(download)) {
                        checkDownloadProcess(download)
                    }
                }
            }.launchIn(CoroutineScope(Dispatchers.IO))
        Timber.d(">>> ===> doWork: Download started " + fusedDownload.name + " " + fusedDownload.status)
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
            isDownloading = false
            unlockMutex()
        }
    }

    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (isDownloading) {
            emit(Unit)
            delay(period)
        }
    }

    private suspend fun checkDownloadProcess(fusedDownload: FusedDownload) {
        try {
            downloadManager.query(downloadManagerQuery.setFilterById(*fusedDownload.downloadIdMap.keys.toLongArray()))
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_FAILED) {
                            fusedManagerRepository.installationIssue(fusedDownload)
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
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
                Timber.d("===> doWork: Installing ${fusedDownload.name} ${fusedDownload.status}")
            }
            Status.INSTALLED, Status.INSTALLATION_ISSUE -> {
                isDownloading = false
                unlockMutex()
                Timber.d("===> doWork: Installed/Failed: ${fusedDownload.name} ${fusedDownload.status}")
            }
            else -> {
                isDownloading = false
                unlockMutex()
                Log.wtf(
                    TAG,
                    "===> ${fusedDownload.name} is in wrong state ${fusedDownload.status}"
                )
            }
        }
    }

    private fun unlockMutex() {
        if (mutex.isLocked) {
            mutex.unlock()
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val title = applicationContext.getString(R.string.app_name)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                "applounge_notification",
                title,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(mChannel)
        }

        val notification = NotificationCompat.Builder(applicationContext, "applounge_notification")
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setOngoing(true)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(atomicInteger.getAndIncrement(), notification)
    }
}
