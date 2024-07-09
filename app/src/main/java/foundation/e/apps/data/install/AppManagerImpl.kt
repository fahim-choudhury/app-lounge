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

package foundation.e.apps.data.install

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
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.parentalcontrol.ContentRatingEntity
import foundation.e.apps.install.download.data.DownloadProgressLD
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
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
class AppManagerImpl @Inject constructor(
    @Named("cacheDir") private val cacheDir: String,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager,
    private val appInstallRepository: AppInstallRepository,
    private val pwaManager: PWAManager,
    private val appLoungePackageManager: AppLoungePackageManager,
    @Named("download") private val downloadNotificationChannel: NotificationChannel,
    @Named("update") private val updateNotificationChannel: NotificationChannel,
    @ApplicationContext private val context: Context
) : AppManager {

    @Inject
    lateinit var contentRatingDao: ContentRatingDao

    private val mutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createNotificationChannels() {
        notificationManager.apply {
            createNotificationChannel(downloadNotificationChannel)
            createNotificationChannel(updateNotificationChannel)
        }
    }

    override suspend fun addDownload(appInstall: AppInstall) {
        appInstall.status = Status.QUEUED
        appInstallRepository.addDownload(appInstall)
    }

    override suspend fun getDownloadById(appInstall: AppInstall): AppInstall? {
        return appInstallRepository.getDownloadById(appInstall.id)
    }

    override suspend fun getDownloadList(): List<AppInstall> {
        return appInstallRepository.getDownloadList()
    }

    override fun getDownloadLiveList(): LiveData<List<AppInstall>> {
        return appInstallRepository.getDownloadLiveList()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun updateDownloadStatus(appInstall: AppInstall, status: Status) {
        if (status == Status.INSTALLED) {
            appInstall.status = status
            insertContentRating(appInstall)
            flushOldDownload(appInstall.packageName)
            appInstallRepository.deleteDownload(appInstall)
        } else if (status == Status.INSTALLING) {
            appInstall.downloadIdMap.all { true }
            appInstall.status = status
            appInstallRepository.updateDownload(appInstall)
            installApp(appInstall)
        }
    }

    private suspend fun insertContentRating(appInstall: AppInstall) {
        contentRatingDao.insertContentRating(
            ContentRatingEntity(
                appInstall.packageName,
                appInstall.contentRating.id,
                appInstall.contentRating.title
            )
        )
        Timber.d("inserted age rating: ${appInstall.contentRating.title}")
    }

    override suspend fun downloadApp(appInstall: AppInstall) {
        mutex.withLock {
            when (appInstall.type) {
                Type.NATIVE -> downloadNativeApp(appInstall)
                Type.PWA -> pwaManager.installPWAApp(appInstall)
            }
        }
    }

    override suspend fun installApp(appInstall: AppInstall) {
        val list = mutableListOf<File>()
        when (appInstall.type) {
            Type.NATIVE -> {
                val parentPathFile = File("$cacheDir/${appInstall.packageName}")
                parentPathFile.listFiles()?.let { list.addAll(it) }
                list.sort()

                if (list.size != 0) {
                    try {
                        Timber.i("installApp: STARTED ${appInstall.name} ${list.size}")
                        appLoungePackageManager.installApplication(list, appInstall.packageName)
                        Timber.i("installApp: ENDED ${appInstall.name} ${list.size}")
                    } catch (e: Exception) {
                        Timber.i(">>> installApp app failed ")
                        installationIssue(appInstall)
                        throw e
                    }
                }
            }
            else -> {
                Timber.d("Unsupported application type!")
                appInstall.status = Status.INSTALLATION_ISSUE
                appInstallRepository.updateDownload(appInstall)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun cancelDownload(appInstall: AppInstall, packageName: String) {
        mutex.withLock {
            if (appInstall.id.isNotBlank()) {
                removeFusedDownload(appInstall)
            } else {
                Timber.d("Unable to cancel download!")
            }
            contentRatingDao.deleteContentRating(packageName)
        }
    }

    private suspend fun removeFusedDownload(appInstall: AppInstall) {
        appInstall.downloadIdMap.forEach { (key, _) ->
            downloadManager.remove(key)
        }
        DownloadProgressLD.setDownloadId(-1)

        if (appInstall.status != Status.INSTALLATION_ISSUE) {
            appInstallRepository.deleteDownload(appInstall)
        }

        flushOldDownload(appInstall.packageName)
    }

    override suspend fun getFusedDownload(downloadId: Long, packageName: String): AppInstall {
        val downloadList = getDownloadList()
        var appInstall = AppInstall()
        downloadList.forEach {
            if (downloadId != 0L) {
                if (it.downloadIdMap.contains(downloadId)) {
                    appInstall = it
                }
            } else if (packageName.isNotBlank()) {
                if (it.packageName == packageName) {
                    appInstall = it
                }
            }
        }
        return appInstall
    }

    override fun flushOldDownload(packageName: String) {
        val parentPathFile = File("$cacheDir/$packageName")
        if (parentPathFile.exists()) parentPathFile.deleteRecursively()
    }

    override suspend fun downloadNativeApp(appInstall: AppInstall) {
        var count = 0
        var parentPath = "$cacheDir/${appInstall.packageName}"

        // Clean old downloads and re-create download dir
        flushOldDownload(appInstall.packageName)
        File(parentPath).mkdirs()

        appInstall.status = Status.DOWNLOADING
        appInstallRepository.updateDownload(appInstall)
        DownloadProgressLD.setDownloadId(-1)
        appInstall.downloadURLList.forEach {
            count += 1
            val packagePath: File = if (appInstall.files.isNotEmpty()) {
                getGplayInstallationPackagePath(appInstall, it, parentPath, count)
            } else {
                File(parentPath, "${appInstall.packageName}_$count.apk")
            }
            val request = DownloadManager.Request(Uri.parse(it))
                .setTitle(if (count == 1) appInstall.name else context.getString(R.string.additional_file_for, appInstall.name))
                .setDestinationUri(Uri.fromFile(packagePath))
            val requestId = downloadManager.enqueue(request)
            DownloadProgressLD.setDownloadId(requestId)
            appInstall.downloadIdMap[requestId] = false
        }
        appInstallRepository.updateDownload(appInstall)
    }

    override fun getGplayInstallationPackagePath(
        appInstall: AppInstall,
        it: String,
        parentPath: String,
        count: Int
    ): File {
        val downloadingFile = appInstall.files[appInstall.downloadURLList.indexOf(it)]
        return if (downloadingFile.type == AuroraFile.FileType.BASE || downloadingFile.type == AuroraFile.FileType.SPLIT) {
            File(parentPath, "${appInstall.packageName}_$count.apk")
        } else {
            createObbFileForDownload(appInstall, it)
        }
    }

    override fun createObbFileForDownload(
        appInstall: AppInstall,
        url: String
    ): File {
        val parentPath =
            context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + appInstall.packageName
        File(parentPath).mkdirs()
        val obbFile = appInstall.files[appInstall.downloadURLList.indexOf(url)]
        return File(parentPath, obbFile.name)
    }

    override fun moveOBBFilesToOBBDirectory(appInstall: AppInstall) {
        appInstall.files.forEach {
            val parentPath =
                context.getExternalFilesDir(null)?.absolutePath + "/Android/obb/" + appInstall.packageName
            val file = File(parentPath, it.name)
            if (file.exists()) {
                val destinationDirectory = Environment.getExternalStorageDirectory()
                    .toString() + "/Android/obb/" + appInstall.packageName
                File(destinationDirectory).mkdirs()
                FileManager.moveFile("$parentPath/", it.name, "$destinationDirectory/")
            }
        }
    }

    override fun getBaseApkPath(appInstall: AppInstall) =
        "$cacheDir/${appInstall.packageName}/${appInstall.packageName}_1.apk"

    override suspend fun installationIssue(appInstall: AppInstall) {
        appInstall.status = Status.INSTALLATION_ISSUE
        appInstallRepository.updateDownload(appInstall)
        flushOldDownload(appInstall.packageName)
    }

    override suspend fun updateAwaiting(appInstall: AppInstall) {
        appInstall.status = Status.AWAITING
        appInstallRepository.updateDownload(appInstall)
    }

    override suspend fun updateUnavailable(appInstall: AppInstall) {
        appInstall.status = Status.UNAVAILABLE
        appInstallRepository.updateDownload(appInstall)
    }

    override suspend fun updateAppInstall(appInstall: AppInstall) {
        appInstallRepository.updateDownload(appInstall)
    }

    override suspend fun insertAppInstallPurchaseNeeded(appInstall: AppInstall) {
        appInstall.status = Status.PURCHASE_NEEDED
        appInstallRepository.addDownload(appInstall)
    }

    override fun isAppInstalled(appInstall: AppInstall): Boolean {
        return appLoungePackageManager.isInstalled(appInstall.packageName)
    }

    override fun getInstallationStatus(appInstall: AppInstall): Status {
        return appLoungePackageManager.getPackageStatus(appInstall.packageName, appInstall.versionCode)
    }
}
