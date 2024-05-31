package foundation.e.apps.data.install

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import foundation.e.apps.data.install.models.AppInstall

@Dao
interface AppInstallDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(appInstall: AppInstall)

    @Query("SELECT * FROM fuseddownload")
    fun getDownloadLiveList(): LiveData<List<AppInstall>>

    @Query("SELECT * FROM fuseddownload")
    suspend fun getDownloadList(): List<AppInstall>

    @Query("SELECT * FROM fuseddownload where id = :id")
    suspend fun getDownloadById(id: String): AppInstall?

    @Query("SELECT * FROM fuseddownload where id = :id")
    fun getDownloadFlowById(id: String): LiveData<AppInstall?>

    @Update
    suspend fun updateDownload(appInstall: AppInstall)

    @Delete
    suspend fun deleteDownload(appInstall: AppInstall)
}
