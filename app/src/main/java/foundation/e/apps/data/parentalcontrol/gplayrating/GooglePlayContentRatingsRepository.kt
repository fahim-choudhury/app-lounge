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

package foundation.e.apps.data.parentalcontrol.gplayrating

import foundation.e.apps.data.parentalcontrol.AgeGroupApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayContentRatingsRepository @Inject constructor(private val ageGroupApi: AgeGroupApi) {

    private var _contentRatingGroups = listOf<GooglePlayContentRatingGroup>()
    val contentRatingGroups: List<GooglePlayContentRatingGroup>
        get() =
            _contentRatingGroups.map { ratingGroup ->
                ratingGroup.copy(ratings = ratingGroup.ratings.map { rating -> rating.lowercase() })
            } // Ratings need to be converted to lowercase

    suspend fun fetchContentRatingData() {
        val response = ageGroupApi.getDefinedAgeGroups()
        if (response.isSuccessful) {
            _contentRatingGroups = response.body() ?: emptyList()
        }
    }
}
