package foundation.e.apps.data.fdroid

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import foundation.e.apps.data.fdroid.models.FdroidEntity

/**
 * Dao interface for storing Fdroid info in DB.
 * Created from [foundation.e.apps.di.DaoModule.getFdroidDao]
 */
@Dao
interface FdroidDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFdroidEntity(fdroidEntity: FdroidEntity)

    @Query("SELECT * FROM FdroidEntity where packageName is :packageName")
    suspend fun getFdroidEntityFromPackageName(packageName: String): FdroidEntity?
}
