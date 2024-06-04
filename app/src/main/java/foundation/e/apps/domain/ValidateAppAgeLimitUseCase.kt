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

package foundation.e.apps.domain

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.blockedApps.Age
import foundation.e.apps.data.blockedApps.ContentRatingGroup
import foundation.e.apps.data.blockedApps.ContentRatingsRepository
import foundation.e.apps.data.blockedApps.ParentalControlRepository
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.DataStoreManager
import timber.log.Timber
import javax.inject.Inject

class ValidateAppAgeLimitUseCase @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val dataStoreManager: DataStoreManager,
    private val contentRatingRepository: ContentRatingsRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val playStoreRepository: PlayStoreRepository
) {

    suspend operator fun invoke(app: AppInstall): ResultSupreme<Boolean> {
        val authData = dataStoreManager.getAuthData()
        val ageGroup = parentalControlRepository.getSelectedAgeGroup()

        return when {
            isParentalControlDisabled(ageGroup) -> ResultSupreme.Success(data = true)
            hasNoContentRating(app, authData) -> ResultSupreme.Error(data = false)
            else -> validateAgeLimit(ageGroup, app)
        }
    }

    private fun validateAgeLimit(
        ageGroup: Age,
        app: AppInstall
    ): ResultSupreme.Success<Boolean> {
        val allowedContentRating =
            contentRatingRepository.contentRatingGroups.find { it.id == ageGroup.toString() }

        Timber.d(
            "Selected age group: $ageGroup \n" +
                    "Content rating: ${app.contentRating.id} \n" +
                    "Allowed content rating: $allowedContentRating"
        )

        return ResultSupreme.Success(isValidAppAgeRating(app, allowedContentRating))
    }

    private suspend fun hasNoContentRating(app: AppInstall, authData: AuthData) =
        !verifyContentRatingExists(app, authData)

    private fun isValidAppAgeRating(
        app: AppInstall,
        allowedContentRating: ContentRatingGroup?
    ) = (app.contentRating.id.isNotEmpty()
            && allowedContentRating?.ratings?.contains(app.contentRating.id) == true)

    private fun isParentalControlDisabled(ageGroup: Age) = ageGroup == Age.PARENTAL_CONTROL_DISABLED

    private suspend fun verifyContentRatingExists(
        app: AppInstall,
        authData: AuthData
    ): Boolean {
        if (app.contentRating.title.isEmpty()) {
            applicationRepository
                .getApplicationDetails(
                    app.id, app.packageName, authData, app.origin
                ).let { (appDetails, resultStatus) ->
                    if (resultStatus == ResultStatus.OK) {
                        app.contentRating = appDetails.contentRating
                    } else {
                        return false
                    }
                }
        }

        if (app.contentRating.id.isEmpty()) {
            app.contentRating =
                playStoreRepository.getContentRatingWithId(
                    app.packageName,
                    app.contentRating
                )
        }

        return app.contentRating.title.isNotEmpty() &&
                app.contentRating.id.isNotEmpty()
    }
}
