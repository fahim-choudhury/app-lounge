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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import java.lang.RuntimeException
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
            Log.d(TAG, "Fused download name $fusedDownloadString")
            fusedDownload = databaseRepository.getDownloadById(fusedDownloadString)
            fusedDownload?.let {
                if (fusedDownload.status != Status.AWAITING) {
                    return@let
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
            Log.e(TAG, "doWork: Failed: ${e.stackTraceToString()}")
            fusedDownload?.let {
                fusedManagerRepository.installationIssue(it)
            }
        } finally {
            Log.d(TAG, "doWork: RESULT SUCCESS: ${fusedDownload?.name}")
            return Result.success()
        }
    }

    private suspend fun startAppInstallationProcess(
        fusedDownload: FusedDownload
    ) {
        fusedManagerRepository.downloadApp(fusedDownload)
        Log.d(TAG, "===> doWork: Download started ${fusedDownload.name} ${fusedDownload.status}")
        if (fusedDownload.type == Type.NATIVE) {
            isDownloading = true
            tickerFlow(1.seconds)
                .onEach {
                    checkDownloadProcess(fusedDownload)
                }.launchIn(CoroutineScope(Dispatchers.IO))
            observeDownload(fusedDownload)
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

        downloadManager.query(downloadManagerQuery.setFilterById(*fusedDownload.downloadIdMap.keys.toLongArray()))
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val totalSizeBytes =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val bytesDownloadedSoFar =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    Log.d(
                        TAG,
                        "checkDownloadProcess: ${fusedDownload.name} $bytesDownloadedSoFar/$totalSizeBytes $status"
                    )
                    if (status == DownloadManager.STATUS_FAILED) {
                        fusedManagerRepository.installationIssue(fusedDownload)
                    }
                }
            }
    }

    private suspend fun observeDownload(
        it: FusedDownload,
    ) {
        databaseRepository.getDownloadFlowById(it.id).takeWhile { isDownloading }
            .collect { fusedDownload ->
                if (fusedDownload == null) {
                    isDownloading = false
                    unlockMutex()
                    return@collect
                }
                Log.d(
                    TAG,
                    "doWork: flow collect ===> ${fusedDownload.name} ${fusedDownload.status}"
                )
                try {
                    handleFusedDownloadStatus(fusedDownload)
                } catch (e: Exception) {
                    Log.e(TAG, "observeDownload: ", e)
                    isDownloading = false
                    unlockMutex()
                }
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
                Log.d(
                    TAG,
                    "===> doWork: Installing ${fusedDownload.name} ${fusedDownload.status}"
                )
            }
            Status.INSTALLED, Status.INSTALLATION_ISSUE -> {
                isDownloading = false
                unlockMutex()
                Log.d(
                    TAG,
                    "===> doWork: Installed/Failed: ${fusedDownload.name} ${fusedDownload.status}"
                )
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
        if(mutex.isLocked) {
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
                NotificationManager.IMPORTANCE_DEFAULT
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
