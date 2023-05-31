package foundation.e.apps.data.exodus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackers(trackerList: List<Tracker>): List<Long>

    @Query("SELECT * FROM Tracker")
    suspend fun getTrackers(): List<Tracker>
}
