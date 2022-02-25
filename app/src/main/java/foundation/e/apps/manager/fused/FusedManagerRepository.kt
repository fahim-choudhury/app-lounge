package foundation.e.apps.manager.fused

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedManagerRepository @Inject constructor(
    private val fusedManagerImpl: FusedManagerImpl
) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannels() {
        return fusedManagerImpl.createNotificationChannels()
    }

    suspend fun downloadApp(fusedDownload: FusedDownload) {
        return fusedManagerImpl.downloadApp(fusedDownload)
    }

    suspend fun addDownload(fusedDownload: FusedDownload) {
        return fusedManagerImpl.addDownload(fusedDownload)
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

    fun installApp(fusedDownload: FusedDownload) {
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
}
