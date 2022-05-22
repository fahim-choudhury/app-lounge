package foundation.e.apps.manager.fused

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.manager.database.DatabaseRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.download.DownloadManagerBR
import foundation.e.apps.manager.download.data.DownloadProgressLD
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import foundation.e.apps.utils.modules.PWAManagerModule
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.aurora.gplayapi.data.models.File as AuroraFile

@Singleton
class FusedManagerImpl @Inject constructor(
    @Named("cacheDir") private val cacheDir: String,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager,
    private val databaseRepository: DatabaseRepository,
    private val pwaManagerModule: PWAManagerModule,
    private val pkgManagerModule: PkgManagerModule,
    @Named("download") private val downloadNotificationChannel: NotificationChannel,
    @Named("update") private val updateNotificationChannel: NotificationChannel,
    @ApplicationContext private val context: Context
) {

    private val TAG = FusedManagerImpl::class.java.simpleName

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels() {
        notificationManager.apply {
            createNotificationChannel(downloadNotificationChannel)
            createNotificationChannel(updateNotificationChannel)
        }
    }

    suspend fun addDownload(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.QUEUED
        databaseRepository.addDownload(fusedDownload)
    }

    suspend fun getDownloadList(): List<FusedDownload> {
        return databaseRepository.getDownloadList()
    }

    fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        return databaseRepository.getDownloadLiveList()
    }

    suspend fun clearInstallationIssue(fusedDownload: FusedDownload) {
        flushOldDownload(fusedDownload.packageName)
        databaseRepository.deleteDownload(fusedDownload)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status) {
        if (status == Status.INSTALLED) {
            fusedDownload.status = status
            databaseRepository.updateDownload(fusedDownload)
            DownloadManagerBR.downloadedList.clear()
            delay(100)
            flushOldDownload(fusedDownload.packageName)
            databaseRepository.deleteDownload(fusedDownload)
        } else if (status == Status.INSTALLING) {
            Log.d(TAG, "updateDownloadStatus: Downloaded ===> ${fusedDownload.name} INSTALLING")
            fusedDownload.downloadIdMap.all { true }
            fusedDownload.status = status
            databaseRepository.updateDownload(fusedDownload)
            delay(100)
            installApp(fusedDownload)
            delay(100)
        }
    }

    private val mutex = Mutex()

    suspend fun downloadApp(fusedDownload: FusedDownload) {
        mutex.withLock {
            when (fusedDownload.type) {
                Type.NATIVE -> downloadNativeApp(fusedDownload)
                Type.PWA -> pwaManagerModule.installPWAApp(fusedDownload)
            }
        }
    }

    suspend fun installApp(fusedDownload: FusedDownload) {
        val list = mutableListOf<File>()
        when (fusedDownload.type) {
            Type.NATIVE -> {
                val parentPathFile = File("$cacheDir/${fusedDownload.packageName}")
                parentPathFile.listFiles()?.let { list.addAll(it) }
                list.sort()
                if (list.size != 0) {
                    try {
                        Log.d(TAG, "installApp: STARTED ${fusedDownload.name} ${list.size}")
                        pkgManagerModule.installApplication(list, fusedDownload.packageName)
                        Log.d(TAG, "installApp: ENDED ${fusedDownload.name} ${list.size}")
                    } catch (e: Exception) {
                        installationIssue(fusedDownload)
                        throw e
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unsupported application type!")
                fusedDownload.status = Status.INSTALLATION_ISSUE
                databaseRepository.updateDownload(fusedDownload)
                delay(100)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun cancelDownload(fusedDownload: FusedDownload) {
        if (fusedDownload.id.isNotBlank()) {
            fusedDownload.downloadIdMap.forEach { (key, _) ->
                downloadManager.remove(key)
            }
            DownloadProgressLD.setDownloadId(-1)
            DownloadManagerBR.downloadedList.clear()

            // Reset the status before deleting download
            updateDownloadStatus(fusedDownload, fusedDownload.orgStatus)
            delay(100)

            databaseRepository.deleteDownload(fusedDownload)
            flushOldDownload(fusedDownload.packageName)
        } else {
            Log.d(TAG, "Unable to cancel download!")
        }
    }

    suspend fun getFusedDownload(downloadId: Long = 0, packageName: String = ""): FusedDownload {
        val downloadList = getDownloadList()
        var fusedDownload = FusedDownload()
        downloadList.forEach {
            if (downloadId != 0L) {
                if (it.downloadIdMap.contains(downloadId)) {
                    fusedDownload = it
                }
            } else if (packageName.isNotBlank()) {
                if (it.packageName == packageName) {
                    fusedDownload = it
                }
            }
        }
        Log.d(TAG, "getFusedDownload: $fusedDownload")
        return fusedDownload
    }

    private fun flushOldDownload(packageName: String) {
        val parentPathFile = File("$cacheDir/$packageName")
        if (parentPathFile.exists()) parentPathFile.deleteRecursively()
    }

    private suspend fun downloadNativeApp(fusedDownload: FusedDownload) {
        var count = 0
        var parentPath = "$cacheDir/${fusedDownload.packageName}"

        // Clean old downloads and re-create download dir
        flushOldDownload(fusedDownload.packageName)
        File(parentPath).mkdirs()

        fusedDownload.status = Status.DOWNLOADING
        databaseRepository.updateDownload(fusedDownload)
        DownloadProgressLD.setDownloadId(-1)
        delay(100)
        Log.d(TAG, "downloadNativeApp: ${fusedDownload.name} ${fusedDownload.downloadURLList.size}")
        fusedDownload.downloadURLList.forEach {
            count += 1
            val packagePath: File = if (fusedDownload.files.isNotEmpty()) {
                getGplayInstallationPackagePath(fusedDownload, it, parentPath, count)
            } else {
                File(parentPath, "${fusedDownload.packageName}_$count.apk")
            }
            Log.d(TAG, "downloadNativeApp: destination path: $packagePath")
            val request = DownloadManager.Request(Uri.parse(it))
                .setTitle(if (count == 1) fusedDownload.name else "Additional file for ${fusedDownload.name}")
                .setDestinationUri(Uri.fromFile(packagePath))
            val requestId = downloadManager.enqueue(request)
            DownloadProgressLD.setDownloadId(requestId)
            fusedDownload.downloadIdMap[requestId] = false
        }
        databaseRepository.updateDownload(fusedDownload)
    }

    private fun getGplayInstallationPackagePath(
        fusedDownload: FusedDownload,
        it: String,
        parentPath: String,
        count: Int
    ): File {
        val downloadingFile = fusedDownload.files[fusedDownload.downloadURLList.indexOf(it)]
        return if (downloadingFile.type == AuroraFile.FileType.BASE || downloadingFile.type == AuroraFile.FileType.SPLIT) {
            File(parentPath, "${fusedDownload.packageName}_$count.apk")
        } else {
            createObbFileForDownload(fusedDownload, it)
        }
    }

    private fun createObbFileForDownload(
        fusedDownload: FusedDownload,
        url: String
    ): File {
        val parentPath =
            context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + fusedDownload.packageName
        File(parentPath).mkdirs()
        val obbFile = fusedDownload.files[fusedDownload.downloadURLList.indexOf(url)]
        return File(parentPath, obbFile.name)
    }

    fun moveOBBFilesToOBBDirectory(fusedDownload: FusedDownload) {
        fusedDownload.files.forEach {
            val parentPath =
                context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + fusedDownload.packageName
            Log.d(TAG, "updateDownloadStatus: source path: $parentPath filename: ${it.name}")
            val file = File(parentPath, it.name)
            if (file.exists()) {
                val destinationDirectory = Environment.getExternalStorageDirectory()
                    .toString() + "/Android/obb/" + fusedDownload.packageName
                Log.d(TAG, "updateDownloadStatus: destination path: $destinationDirectory")
                File(destinationDirectory).mkdirs()
                FileManager.moveFile("$parentPath/", it.name, "$destinationDirectory/")
            }
        }
    }

    suspend fun installationIssue(fusedDownload: FusedDownload) {
        flushOldDownload(fusedDownload.packageName)
        fusedDownload.status = Status.INSTALLATION_ISSUE
        databaseRepository.updateDownload(fusedDownload)
    }

    suspend fun updateAwaiting(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.AWAITING
        databaseRepository.updateDownload(fusedDownload)
    }

    suspend fun updateUnavailable(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.UNAVAILABLE
        databaseRepository.updateDownload(fusedDownload)
    }

    suspend fun updateFusedDownload(fusedDownload: FusedDownload) {
        databaseRepository.updateDownload(fusedDownload)
    }

    suspend fun insertFusedDownloadPurchaseNeeded(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.PURCHASE_NEEDED
        databaseRepository.addDownload(fusedDownload)
    }
}
