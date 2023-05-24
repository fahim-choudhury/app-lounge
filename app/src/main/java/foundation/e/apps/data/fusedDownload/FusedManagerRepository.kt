package foundation.e.apps.data.fusedDownload

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import foundation.e.apps.OpenForTesting
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.workmanager.InstallWorkManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class FusedManagerRepository @Inject constructor(
    private val fusedManagerImpl: IFusedManager,
    private val fdroidRepository: FdroidRepository
) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels() {
        return fusedManagerImpl.createNotificationChannels()
    }

    suspend fun downloadApp(fusedDownload: FusedDownload) {
        return fusedManagerImpl.downloadApp(fusedDownload)
    }

    fun moveOBBFileToOBBDirectory(fusedDownload: FusedDownload) {
        return fusedManagerImpl.moveOBBFilesToOBBDirectory(fusedDownload)
    }

    suspend fun addDownload(fusedDownload: FusedDownload): Boolean {
        val existingFusedDownload = fusedManagerImpl.getDownloadById(fusedDownload)
        if (isInstallWorkRunning(existingFusedDownload, fusedDownload)) {
            return false
        }

        // We don't want to add any thing, if it already exists without INSTALLATION_ISSUE
        if (existingFusedDownload != null && existingFusedDownload.status != Status.INSTALLATION_ISSUE) {
            return false
        }

        fusedManagerImpl.addDownload(fusedDownload)
        return true
    }

    private fun isInstallWorkRunning(
        existingFusedDownload: FusedDownload?,
        fusedDownload: FusedDownload
    ) =
        existingFusedDownload != null && InstallWorkManager.checkWorkIsAlreadyAvailable(
            fusedDownload.id
        )

    suspend fun addFusedDownloadPurchaseNeeded(fusedDownload: FusedDownload) {
        fusedManagerImpl.insertFusedDownloadPurchaseNeeded(fusedDownload)
    }

    suspend fun clearInstallationIssue(fusedDownload: FusedDownload) {
        return fusedManagerImpl.clearInstallationIssue(fusedDownload)
    }

    suspend fun getDownloadList(): List<FusedDownload> {
        return fusedManagerImpl.getDownloadList()
    }

    fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        return fusedManagerImpl.getDownloadLiveList()
    }

    fun getDownloadListFlow(): Flow<List<FusedDownload>> {
        return fusedManagerImpl.getDownloadLiveList().asFlow()
    }

    suspend fun installApp(fusedDownload: FusedDownload) {
        return fusedManagerImpl.installApp(fusedDownload)
    }

    suspend fun getFusedDownload(downloadId: Long = 0, packageName: String = ""): FusedDownload {
        return fusedManagerImpl.getFusedDownload(downloadId, packageName)
    }

    suspend fun updateDownloadStatus(fusedDownload: FusedDownload, status: Status) {
        return fusedManagerImpl.updateDownloadStatus(fusedDownload, status)
    }

    suspend fun cancelDownload(fusedDownload: FusedDownload) {
        return fusedManagerImpl.cancelDownload(fusedDownload)
    }

    suspend fun installationIssue(fusedDownload: FusedDownload) {
        return fusedManagerImpl.installationIssue(fusedDownload)
    }

    suspend fun updateAwaiting(fusedDownload: FusedDownload) {
        fusedManagerImpl.updateAwaiting(fusedDownload)
    }

    suspend fun updateUnavailable(fusedDownload: FusedDownload) {
        fusedManagerImpl.updateUnavailable(fusedDownload)
    }

    suspend fun updateFusedDownload(fusedDownload: FusedDownload) {
        fusedManagerImpl.updateFusedDownload(fusedDownload)
    }

    fun validateFusedDownload(fusedDownload: FusedDownload) =
        fusedDownload.packageName.isNotEmpty() && fusedDownload.downloadURLList.isNotEmpty()

    suspend fun calculateProgress(
        fusedApp: FusedApp?,
        progress: DownloadProgress
    ): Int {
        fusedApp?.let { app ->
            val appDownload = getDownloadList()
                .singleOrNull { it.id.contentEquals(app._id) && it.packageName.contentEquals(app.package_name) }
                ?: return 0

            if (!appDownload.id.contentEquals(app._id) || !appDownload.packageName.contentEquals(app.package_name)) {
                return@let
            }

            if (!isProgressValidForApp(fusedApp, progress)) {
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
        fusedApp: FusedApp,
        downloadProgress: DownloadProgress
    ): Boolean {
        val download = getFusedDownload(downloadProgress.downloadId)
        return download.id == fusedApp._id
    }

    fun handleRatingFormat(rating: Double): String {
        return if (rating % 1 == 0.0) {
            rating.toInt().toString()
        } else {
            rating.toString()
        }
    }

    suspend fun getCalculateProgressWithTotalSize(fusedApp: FusedApp?, progress: DownloadProgress): Pair<Long, Long> {
        fusedApp?.let { app ->
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

    fun getDownloadingItemStatus(fusedApp: FusedApp?, downloadList: List<FusedDownload>): Status? {
        fusedApp?.let { app ->
            val downloadingItem =
                downloadList.find { it.origin == app.origin && (it.packageName == app.package_name || it.id == app.package_name) }
            return downloadingItem?.status
        }
        return null
    }

    suspend fun isFdroidApplicationSigned(context: Context, fusedDownload: FusedDownload): Boolean {
        val apkFilePath = fusedManagerImpl.getBaseApkPath(fusedDownload)
        return fdroidRepository.isFdroidApplicationSigned(context, fusedDownload.packageName, apkFilePath, fusedDownload.signature)
    }

    fun isFusedDownloadInstalled(fusedDownload: FusedDownload): Boolean {
        return fusedManagerImpl.isFusedDownloadInstalled(fusedDownload)
    }

    fun getFusedDownloadPackageStatus(fusedDownload: FusedDownload): Status {
        return fusedManagerImpl.getFusedDownloadInstallationStatus(fusedDownload)
    }
}
