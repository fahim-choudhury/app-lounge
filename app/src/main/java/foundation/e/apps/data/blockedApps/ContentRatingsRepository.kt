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

import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.helpers.ContentRatingHelper
import foundation.e.apps.data.ageRating.AgeGroupApi
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.login.AuthenticatorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRatingsRepository @Inject constructor(
    private val ageGroupApi: AgeGroupApi,
    private val authenticatorRepository: AuthenticatorRepository,
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

    suspend fun getEnglishContentRating(packageName: String): ContentRating? {
        val authData = authenticatorRepository.gplayAuth!!
        val contentRatingHelper = ContentRatingHelper(authData)

        return handleNetworkResult {
            contentRatingHelper.getEnglishContentRating(packageName)
        }.data
    }
}
