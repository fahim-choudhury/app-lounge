/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.data.parentalcontrol

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingGroup

@Entity
data class ContentRatingEntity(
    @PrimaryKey val packageName: String,
    val ratingId: String,
    val ratingTitle: String,
)

@Entity
data class FDroidNsfwApp(
    @PrimaryKey val packageName: String
)

@Dao
interface ContentRatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentRatingGroups(ageGroups: List<GPlayContentRatingGroup>)

    @Query("SELECT * FROM GPlayContentRatingGroup")
    suspend fun getAllContentRatingGroups(): List<GPlayContentRatingGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFDroidNsfwApp(nsfwApps: List<FDroidNsfwApp>)

    @Query("SELECT * FROM FDroidNsfwApp")
    suspend fun getAllFDroidNsfwApp(): List<FDroidNsfwApp>

    @Query("DELETE FROM FDroidNsfwApp")
    suspend fun clearFDroidNsfwApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentRating(contentRatingEntity: ContentRatingEntity)

    @Query("DELETE FROM ContentRatingEntity WHERE packageName = :packageName")
    suspend fun deleteContentRating(packageName: String)

    @Query("SELECT * FROM ContentRatingEntity WHERE packageName = :packageName LIMIT 1")
    suspend fun getContentRating(packageName: String): ContentRatingEntity?

}