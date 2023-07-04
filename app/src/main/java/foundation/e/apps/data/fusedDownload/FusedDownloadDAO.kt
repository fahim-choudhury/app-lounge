package foundation.e.apps.data.fusedDownload

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import foundation.e.apps.data.fusedDownload.models.FusedDownload

@Dao
interface FusedDownloadDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(fusedDownload: FusedDownload)

    @Query("SELECT * FROM fuseddownload")
    fun getDownloadLiveList(): LiveData<List<FusedDownload>>

    @Query("SELECT * FROM fuseddownload")
    suspend fun getDownloadList(): List<FusedDownload>

    @Query("SELECT * FROM fuseddownload where id = :id")
    suspend fun getDownloadById(id: String): FusedDownload?

    @Query("SELECT * FROM fuseddownload where id = :id")
    fun getDownloadFlowById(id: String): LiveData<FusedDownload?>

    @Update
    suspend fun updateDownload(fusedDownload: FusedDownload)

    @Delete
    suspend fun deleteDownload(fusedDownload: FusedDownload)
}
