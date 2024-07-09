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

package foundation.e.apps.data.parentalcontrol.googleplay

import com.aurora.gplayapi.data.models.ContentRating
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.playstore.PlayStoreRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GPlayContentRatingRepository @Inject constructor(
    private val ageGroupApi: AgeGroupApi,
    private val playStoreRepository: PlayStoreRepository,
    private val contentRatingDao: ContentRatingDao,
) {

    private var _contentRatingGroups = listOf<GPlayContentRatingGroup>()
    val contentRatingGroups: List<GPlayContentRatingGroup>
        get() = _contentRatingGroups

    suspend fun fetchContentRatingData() {
        val fetchedContentRatingGroups = getFromApi()

        _contentRatingGroups = if (fetchedContentRatingGroups.isEmpty()) {
            getFromDb()
        } else {
            fetchedContentRatingGroups.also {
                contentRatingDao.insertContentRatingGroups(it)
            }
        }
    }

    private suspend fun getFromDb(): List<GPlayContentRatingGroup> {
        return contentRatingDao.getAllContentRatingGroups()
    }

    private suspend fun getFromApi(): List<GPlayContentRatingGroup> {
        return runCatching {
            ageGroupApi.getDefinedAgeGroups().body() ?: emptyList()
        }.getOrElse { emptyList() }
    }

    suspend fun getEnglishContentRating(packageName: String): ContentRating? {
        return handleNetworkResult {
            playStoreRepository.getEnglishContentRating(packageName)
        }.data
    }
}
