package foundation.e.apps.manager.fused

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import foundation.e.apps.api.fdroid.FdroidRepository
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedManagerRepository @Inject constructor(
    private val fusedManagerImpl: FusedManagerImpl,
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

    suspend fun addDownload(fusedDownload: FusedDownload) {
        return fusedManagerImpl.addDownload(fusedDownload)
    }

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

    suspend fun isFdroidApplicationSigned(context: Context, fusedDownload: FusedDownload): Boolean {
        val apkFilePath = fusedManagerImpl.getBaseApkPath(fusedDownload)
        return fdroidRepository.isFdroidApplicationSigned(context, fusedDownload.packageName, apkFilePath, fusedDownload.signature)
    }
}
