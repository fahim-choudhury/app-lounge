package foundation.e.apps.manager.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import foundation.e.apps.OpenForTesting
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.database.fusedDownload.FusedDownloadDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class DatabaseRepository @Inject constructor(
    private val fusedDownloadDAO: FusedDownloadDAO
) {

    private val mutex = Mutex()

    suspend fun addDownload(fusedDownload: FusedDownload) {
        mutex.withLock {
            return fusedDownloadDAO.addDownload(fusedDownload)
        }
    }

    suspend fun getDownloadList(): List<FusedDownload> {
        mutex.withLock {
            return fusedDownloadDAO.getDownloadList()
        }
    }

    fun getDownloadLiveList(): LiveData<List<FusedDownload>> {
        return fusedDownloadDAO.getDownloadLiveList()
    }

    suspend fun updateDownload(fusedDownload: FusedDownload) {
        mutex.withLock {
            fusedDownloadDAO.updateDownload(fusedDownload)
        }
    }

    suspend fun deleteDownload(fusedDownload: FusedDownload) {
        mutex.withLock {
            return fusedDownloadDAO.deleteDownload(fusedDownload)
        }
    }

    suspend fun getDownloadById(id: String): FusedDownload? {
        mutex.withLock {
            return fusedDownloadDAO.getDownloadById(id)
        }
    }

    fun getDownloadFlowById(id: String): Flow<FusedDownload?> {
        return fusedDownloadDAO.getDownloadFlowById(id).asFlow()
    }
}
