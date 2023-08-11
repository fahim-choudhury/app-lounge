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

package foundation.e.apps.data.fusedDownload

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.install.download.data.DownloadProgressLD
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
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
    private val fusedDownloadRepository: FusedDownloadRepository,
    private val pwaManagerModule: PWAManagerModule,
    private val pkgManagerModule: PkgManagerModule,
    @Named("download") private val downloadNotificationChannel: NotificationChannel,
    @Named("update") private val updateNotificationChannel: NotificationChannel,
    @ApplicationContext private val context: Context
) : IFusedManager {

    private val mutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createNotificationChannels() {
        notificationManager.apply {
            createNotificationChannel(downloadNotificationChannel)
            createNotificationChannel(updateNotificationChannel)
        }
    }

    override suspend fun addDownload(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.QUEUED
        fusedDownloadRepository.addDownload(fusedDownload)
    }

    override suspend fun getDownloadById(fusedDownload: FusedDownload): FusedDownload? {
        return fusedDownloadRepository.getDownloadById(fusedDownload.id)
    }

    override suspend fun getDownloadList(): List<FusedDownload> {
        return fusedDownloadRepository.getDownloadList()
    }

    override fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        return fusedDownloadRepository.getDownloadLiveList()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status) {
        if (status == Status.INSTALLED) {
            fusedDownload.status = status
            flushOldDownload(fusedDownload.packageName)
            fusedDownloadRepository.deleteDownload(fusedDownload)
        } else if (status == Status.INSTALLING) {
            fusedDownload.downloadIdMap.all { true }
            fusedDownload.status = status
            fusedDownloadRepository.updateDownload(fusedDownload)
            installApp(fusedDownload)
        }
    }

    override suspend fun downloadApp(fusedDownload: FusedDownload) {
        mutex.withLock {
            when (fusedDownload.type) {
                Type.NATIVE -> downloadNativeApp(fusedDownload)
                Type.PWA -> pwaManagerModule.installPWAApp(fusedDownload)
            }
        }
    }

    override suspend fun installApp(fusedDownload: FusedDownload) {
        val list = mutableListOf<File>()
        when (fusedDownload.type) {
            Type.NATIVE -> {
                val parentPathFile = File("$cacheDir/${fusedDownload.packageName}")
                parentPathFile.listFiles()?.let { list.addAll(it) }
                list.sort()

                if (list.size != 0) {
                    try {
                        Timber.i("installApp: STARTED ${fusedDownload.name} ${list.size}")
                        pkgManagerModule.installApplication(list, fusedDownload.packageName)
                        Timber.i("installApp: ENDED ${fusedDownload.name} ${list.size}")
                    } catch (e: Exception) {
                        Timber.i(">>> installApp app failed ")
                        installationIssue(fusedDownload)
                        throw e
                    }
                }
            }
            else -> {
                Timber.d("Unsupported application type!")
                fusedDownload.status = Status.INSTALLATION_ISSUE
                fusedDownloadRepository.updateDownload(fusedDownload)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun cancelDownload(fusedDownload: FusedDownload) {
        mutex.withLock {
            if (fusedDownload.id.isNotBlank()) {
                removeFusedDownload(fusedDownload)
            } else {
                Timber.d("Unable to cancel download!")
            }
        }
    }

    private suspend fun removeFusedDownload(fusedDownload: FusedDownload) {
        fusedDownload.downloadIdMap.forEach { (key, _) ->
            downloadManager.remove(key)
        }
        DownloadProgressLD.setDownloadId(-1)

        if (fusedDownload.status != Status.INSTALLATION_ISSUE) {
            fusedDownloadRepository.deleteDownload(fusedDownload)
        }

        flushOldDownload(fusedDownload.packageName)
    }

    override suspend fun getFusedDownload(downloadId: Long, packageName: String): FusedDownload {
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
        return fusedDownload
    }

    override fun flushOldDownload(packageName: String) {
        val parentPathFile = File("$cacheDir/$packageName")
        if (parentPathFile.exists()) parentPathFile.deleteRecursively()
    }

    override suspend fun downloadNativeApp(fusedDownload: FusedDownload) {
        var count = 0
        var parentPath = "$cacheDir/${fusedDownload.packageName}"

        // Clean old downloads and re-create download dir
        flushOldDownload(fusedDownload.packageName)
        File(parentPath).mkdirs()

        fusedDownload.status = Status.DOWNLOADING
        fusedDownloadRepository.updateDownload(fusedDownload)
        DownloadProgressLD.setDownloadId(-1)
        fusedDownload.downloadURLList.forEach {
            count += 1
            val packagePath: File = if (fusedDownload.files.isNotEmpty()) {
                getGplayInstallationPackagePath(fusedDownload, it, parentPath, count)
            } else {
                File(parentPath, "${fusedDownload.packageName}_$count.apk")
            }
            val request = DownloadManager.Request(Uri.parse(it))
                .setTitle(if (count == 1) fusedDownload.name else context.getString(R.string.additional_file_for, fusedDownload.name))
                .setDestinationUri(Uri.fromFile(packagePath))
            val requestId = downloadManager.enqueue(request)
            DownloadProgressLD.setDownloadId(requestId)
            fusedDownload.downloadIdMap[requestId] = false
        }
        fusedDownloadRepository.updateDownload(fusedDownload)
    }

    override fun getGplayInstallationPackagePath(
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

    override fun createObbFileForDownload(
        fusedDownload: FusedDownload,
        url: String
    ): File {
        val parentPath =
            context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + fusedDownload.packageName
        File(parentPath).mkdirs()
        val obbFile = fusedDownload.files[fusedDownload.downloadURLList.indexOf(url)]
        return File(parentPath, obbFile.name)
    }

    override fun moveOBBFilesToOBBDirectory(fusedDownload: FusedDownload) {
        fusedDownload.files.forEach {
            val parentPath =
                context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + fusedDownload.packageName
            val file = File(parentPath, it.name)
            if (file.exists()) {
                val destinationDirectory = Environment.getExternalStorageDirectory()
                    .toString() + "/Android/obb/" + fusedDownload.packageName
                File(destinationDirectory).mkdirs()
                FileManager.moveFile("$parentPath/", it.name, "$destinationDirectory/")
            }
        }
    }

    override fun getBaseApkPath(fusedDownload: FusedDownload) =
        "$cacheDir/${fusedDownload.packageName}/${fusedDownload.packageName}_1.apk"

    override suspend fun installationIssue(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.INSTALLATION_ISSUE
        fusedDownloadRepository.updateDownload(fusedDownload)
        flushOldDownload(fusedDownload.packageName)
    }

    override suspend fun updateAwaiting(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.AWAITING
        fusedDownloadRepository.updateDownload(fusedDownload)
    }

    override suspend fun updateUnavailable(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.UNAVAILABLE
        fusedDownloadRepository.updateDownload(fusedDownload)
    }

    override suspend fun updateFusedDownload(fusedDownload: FusedDownload) {
        fusedDownloadRepository.updateDownload(fusedDownload)
    }

    override suspend fun insertFusedDownloadPurchaseNeeded(fusedDownload: FusedDownload) {
        fusedDownload.status = Status.PURCHASE_NEEDED
        fusedDownloadRepository.addDownload(fusedDownload)
    }

    override fun isFusedDownloadInstalled(fusedDownload: FusedDownload): Boolean {
        return pkgManagerModule.isInstalled(fusedDownload.packageName)
    }

    override fun getFusedDownloadInstallationStatus(fusedApp: FusedDownload): Status {
        return pkgManagerModule.getPackageStatus(fusedApp.packageName, fusedApp.versionCode)
    }
}
