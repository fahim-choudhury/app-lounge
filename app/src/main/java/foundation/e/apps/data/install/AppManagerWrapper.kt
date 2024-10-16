package foundation.e.apps.data.install

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import foundation.e.apps.OpenForTesting
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.workmanager.InstallWorkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class AppManagerWrapper @Inject constructor(
    private val appManager: AppManager,
    private val fdroidRepository: FdroidRepository
) {

    fun createNotificationChannels() {
        return appManager.createNotificationChannels()
    }

    suspend fun downloadApp(appInstall: AppInstall) {
        return appManager.downloadApp(appInstall)
    }

    fun moveOBBFileToOBBDirectory(appInstall: AppInstall) {
        return appManager.moveOBBFilesToOBBDirectory(appInstall)
    }

    suspend fun addDownload(appInstall: AppInstall): Boolean {
        val existingFusedDownload = appManager.getDownloadById(appInstall)
        if (isInstallWorkRunning(existingFusedDownload, appInstall)) {
            return false
        }

        // We don't want to add any thing, if it already exists without INSTALLATION_ISSUE
        if (existingFusedDownload != null && !isStatusEligibleToInstall(existingFusedDownload)) {
            return false
        }

        appManager.addDownload(appInstall)
        return true
    }

    private fun isStatusEligibleToInstall(existingAppInstall: AppInstall) =
        listOf(
            Status.UNAVAILABLE,
            Status.INSTALLATION_ISSUE,
            Status.PURCHASE_NEEDED
        ).contains(existingAppInstall.status)

    private fun isInstallWorkRunning(
        existingAppInstall: AppInstall?,
        appInstall: AppInstall
    ) =
        existingAppInstall != null && InstallWorkManager.checkWorkIsAlreadyAvailable(
            appInstall.id
        )

    suspend fun addFusedDownloadPurchaseNeeded(appInstall: AppInstall) {
        appManager.insertAppInstallPurchaseNeeded(appInstall)
    }

    suspend fun getDownloadList(): List<AppInstall> {
        return appManager.getDownloadList()
    }

    fun getDownloadLiveList(): LiveData<List<AppInstall>> {
        return appManager.getDownloadLiveList()
    }

    suspend fun getFusedDownload(downloadId: Long = 0, packageName: String = ""): AppInstall {
        return appManager.getFusedDownload(downloadId, packageName)
    }

    suspend fun updateDownloadStatus(appInstall: AppInstall, status: Status) {
        return appManager.updateDownloadStatus(appInstall, status)
    }

    suspend fun cancelDownload(appInstall: AppInstall, packageName: String = "") {
        return appManager.cancelDownload(appInstall, packageName)
    }

    suspend fun installationIssue(appInstall: AppInstall) {
        return appManager.installationIssue(appInstall)
    }

    suspend fun updateAwaiting(appInstall: AppInstall) {
        appManager.updateAwaiting(appInstall)
    }

    suspend fun updateUnavailable(appInstall: AppInstall) {
        appManager.updateUnavailable(appInstall)
    }

    suspend fun updateFusedDownload(appInstall: AppInstall) {
        appManager.updateAppInstall(appInstall)
    }

    fun validateFusedDownload(appInstall: AppInstall) =
        appInstall.packageName.isNotEmpty() && appInstall.downloadURLList.isNotEmpty()

    suspend fun calculateProgress(
        application: Application?,
        progress: DownloadProgress
    ): Int {
        application?.let { app ->
            val appDownload = getDownloadList()
                .singleOrNull { it.id.contentEquals(app._id) && it.packageName.contentEquals(app.package_name) }
                ?: return 0

            if (!appDownload.id.contentEquals(app._id) || !appDownload.packageName.contentEquals(app.package_name)) {
                return@let
            }

            if (!isProgressValidForApp(application, progress)) {
                return -1
            }

            val downloadingMap = progress.totalSizeBytes.filter { item ->
                appDownload.downloadIdMap.keys.contains(item.key) && item.value > 0
            }

            if (appDownload.downloadIdMap.size > downloadingMap.size) { // All files for download are not ready yet
                return 0
            }

            val totalSizeBytes = downloadingMap.values.sum()
            val downloadedSoFar = progress.bytesDownloadedSoFar.filter { item ->
                appDownload.downloadIdMap.keys.contains(item.key)
            }.values.sum()
            return ((downloadedSoFar / totalSizeBytes.toDouble()) * 100).toInt()
        }
        return 0
    }

    private suspend fun isProgressValidForApp(
        application: Application,
        downloadProgress: DownloadProgress
    ): Boolean {
        val download = getFusedDownload(downloadProgress.downloadId)
        return download.id == application._id
    }

    fun handleRatingFormat(rating: Double): String {
        return if (rating % 1 == 0.0) {
            rating.toInt().toString()
        } else {
            rating.toString()
        }
    }

    suspend fun getCalculateProgressWithTotalSize(application: Application?, progress: DownloadProgress): Pair<Long, Long> {
        application?.let { app ->
            val appDownload = getDownloadList()
                .singleOrNull { it.id.contentEquals(app._id) }
            val downloadingMap = progress.totalSizeBytes.filter { item ->
                appDownload?.downloadIdMap?.keys?.contains(item.key) == true
            }
            val totalSizeBytes = downloadingMap.values.sum()
            val downloadedSoFar = progress.bytesDownloadedSoFar.filter { item ->
                appDownload?.downloadIdMap?.keys?.contains(item.key) == true
            }.values.sum()

            return Pair(totalSizeBytes, downloadedSoFar)
        }
        return Pair(1, 0)
    }

    fun getDownloadingItemStatus(application: Application?, downloadList: List<AppInstall>): Status? {
        application?.let { app ->
            val downloadingItem =
                downloadList.find { it.origin == app.origin && (it.packageName == app.package_name || it.id == app.package_name) }
            return downloadingItem?.status
        }
        return null
    }

    suspend fun isFdroidApplicationSigned(context: Context, appInstall: AppInstall): Boolean {
        val apkFilePath = appManager.getBaseApkPath(appInstall)
        return fdroidRepository.isFdroidApplicationSigned(context, appInstall.packageName, apkFilePath, appInstall.signature)
    }

    fun isFusedDownloadInstalled(appInstall: AppInstall): Boolean {
        return appManager.isAppInstalled(appInstall)
    }

    fun getFusedDownloadPackageStatus(appInstall: AppInstall): Status {
        return appManager.getInstallationStatus(appInstall)
    }
}
