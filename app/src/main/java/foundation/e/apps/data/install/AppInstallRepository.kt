package foundation.e.apps.data.install

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import foundation.e.apps.OpenForTesting
import foundation.e.apps.data.install.models.AppInstall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class AppInstallRepository @Inject constructor(
    private val appInstallDAO: AppInstallDAO
) {

    private val mutex = Mutex()

    suspend fun addDownload(appInstall: AppInstall) {
        mutex.withLock {
            return appInstallDAO.addDownload(appInstall)
        }
    }

    suspend fun getDownloadList(): List<AppInstall> {
        mutex.withLock {
            return appInstallDAO.getDownloadList()
        }
    }

    fun getDownloadLiveList(): LiveData<List<AppInstall>> {
        return appInstallDAO.getDownloadLiveList()
    }

    suspend fun updateDownload(appInstall: AppInstall) {
        mutex.withLock {
            appInstallDAO.updateDownload(appInstall)
        }
    }

    suspend fun deleteDownload(appInstall: AppInstall) {
        mutex.withLock {
            return appInstallDAO.deleteDownload(appInstall)
        }
    }

    suspend fun getDownloadById(id: String): AppInstall? {
        mutex.withLock {
            return appInstallDAO.getDownloadById(id)
        }
    }

    fun getDownloadFlowById(id: String): Flow<AppInstall?> {
        return appInstallDAO.getDownloadFlowById(id).asFlow()
    }
}
