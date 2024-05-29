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

package foundation.e.apps.domain

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.blockedApps.ContentRatingsRepository
import foundation.e.apps.data.blockedApps.ParentalControlRepository
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.preference.DataStoreManager
import timber.log.Timber
import javax.inject.Inject

class CheckAppAgeLimitUseCase @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val dataStoreManager: DataStoreManager,
    private val contentRatingRepository: ContentRatingsRepository,
    private val parentalControlRepository: ParentalControlRepository
) {

    suspend operator fun invoke(fusedDownload: FusedDownload): Boolean {
        val authData = dataStoreManager.getAuthData()
        if (fusedDownload.contentRating?.title?.isEmpty() == true) {
            updateContentRating(fusedDownload, authData)
        }

        val selectedAgeGroup = parentalControlRepository.getSelectedAgeGroup()
        val allowedContentRating = contentRatingRepository.contentRatings.find {
            it.id == selectedAgeGroup.toString()
        }

        Timber.d("Selected age group: $selectedAgeGroup \n" +
                "Content rating: ${fusedDownload.contentRating?.title} \n" +
                "Allowed content rating: $allowedContentRating")
        return selectedAgeGroup != null
                && fusedDownload.contentRating?.title?.isNotEmpty() == true
                && allowedContentRating?.ratings?.contains(fusedDownload.contentRating!!.title) == false
    }

    private suspend fun updateContentRating(
        fusedDownload: FusedDownload,
        authData: AuthData
    ) {
        applicationRepository.getApplicationDetails(
            fusedDownload.id,
            fusedDownload.packageName,
            authData,
            fusedDownload.origin
        ).let { (appDetails, resultStatus) ->
            if (resultStatus == ResultStatus.OK) {
                fusedDownload.contentRating = appDetails.contentRating
            }
        }
    }
}