/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.data.blockedApps

import foundation.e.apps.data.ageRating.AgeGroupApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRatingsRepository @Inject constructor(
    private val ageGroupApi: AgeGroupApi,
) {

    private var _contentRatingGroups = listOf<ContentRatingGroup>()
    val contentRatingGroups: List<ContentRatingGroup>
        get() = _contentRatingGroups

    suspend fun fetchContentRatingData() {
        val response = ageGroupApi.getDefinedAgeGroups()
        if (response.isSuccessful) {
            _contentRatingGroups = response.body() ?: emptyList()
        }
    }
}
