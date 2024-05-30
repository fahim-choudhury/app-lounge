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
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.DataStoreManager
import timber.log.Timber
import javax.inject.Inject

class CheckAppAgeLimitUseCase @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val dataStoreManager: DataStoreManager,
    private val contentRatingRepository: ContentRatingsRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val playStoreRepository: PlayStoreRepository
) {

    suspend operator fun invoke(appInstall: AppInstall): Boolean {
        val authData = dataStoreManager.getAuthData()

        verifyContentRatingExists(appInstall, authData)

        val selectedAgeGroup = parentalControlRepository.getSelectedAgeGroup()
        val allowedContentRating = contentRatingRepository.contentRatingGroups.find {
            it.id == selectedAgeGroup.toString()
        }

        Timber.d(
            "Selected age group: $selectedAgeGroup \n" +
                    "Content rating: ${appInstall.contentRating.id} \n" +
                    "Allowed content rating: $allowedContentRating"
        )
        return selectedAgeGroup != null
                && appInstall.contentRating.id.isNotEmpty()
                && allowedContentRating?.ratings?.contains(appInstall.contentRating.id) == false
    }

    private suspend fun verifyContentRatingExists(
        appInstall: AppInstall,
        authData: AuthData
    ) {
        if (appInstall.contentRating.title.isEmpty()) {
            applicationRepository.getApplicationDetails(
                appInstall.id,
                appInstall.packageName,
                authData,
                appInstall.origin
            ).let { (appDetails, resultStatus) ->
                if (resultStatus == ResultStatus.OK) {
                    appInstall.contentRating = appDetails.contentRating
                }
                // todo: handle unhappy path and return from this method
            }
        }

        if (appInstall.contentRating.id.isEmpty()) {
            appInstall.contentRating = playStoreRepository.getContentRatingWithId(
                appInstall.packageName,
                appInstall.contentRating
            )
        }
    }
}
