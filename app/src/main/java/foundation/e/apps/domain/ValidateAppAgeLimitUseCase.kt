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

import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.parentalcontrol.Age
import foundation.e.apps.data.parentalcontrol.ParentalControlRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.parentalcontrol.fdroid.FDroidAntiFeatureRepository
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingGroup
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingRepository
import timber.log.Timber
import javax.inject.Inject

class ValidateAppAgeLimitUseCase @Inject constructor(
    private val gPlayContentRatingRepository: GPlayContentRatingRepository,
    private val fDroidAntiFeatureRepository: FDroidAntiFeatureRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val appsApi: AppsApi,
) {

    companion object {
        const val KEY_ANTI_FEATURES_NSFW = "NSFW"
    }

    suspend operator fun invoke(app: AppInstall): ResultSupreme<Boolean> {
        val ageGroup = parentalControlRepository.getSelectedAgeGroup()

        return when {
            isParentalControlDisabled(ageGroup) -> ResultSupreme.Success(data = true)
            isKnownNsfwApp(app) -> ResultSupreme.Success(data = false)
            isCleanApkApp(app) -> ResultSupreme.Success(!isNsfwAppByCleanApkApi(app))
            isWhiteListedCleanApkApp(app) -> ResultSupreme.Success(data = true)
            // Check for GPlay apps now
            hasNoContentRatingOnGPlay(app) -> ResultSupreme.Error()
            else -> validateAgeLimit(ageGroup, app)
        }
    }

    private fun isCleanApkApp(app: AppInstall): Boolean {
        return app.id.isNotBlank()
                && app.origin == Origin.CLEANAPK
                && app.type == Type.NATIVE
    }

    private fun isWhiteListedCleanApkApp(app: AppInstall): Boolean {
        return app.origin == Origin.CLEANAPK
    }

    private suspend fun isNsfwAppByCleanApkApi(app: AppInstall): Boolean {
        return appsApi.getCleanapkAppDetails(app.packageName).first.let {
            it.antiFeatures.any { antiFeature ->
                antiFeature.containsKey(KEY_ANTI_FEATURES_NSFW)
            }
        }
    }

    private fun isKnownNsfwApp(app: AppInstall): Boolean {
        return app.packageName in fDroidAntiFeatureRepository.fDroidNsfwApps
    }

    private fun validateAgeLimit(
        ageGroup: Age,
        app: AppInstall
    ): ResultSupreme.Success<Boolean> {
        val allowedContentRating =
            gPlayContentRatingRepository.contentRatingGroups.find { it.id == ageGroup.toString() }

        Timber.d(
            "Selected age group: $ageGroup \n" +
                    "Content rating: ${app.contentRating.id} \n" +
                    "Allowed content rating: $allowedContentRating"
        )

        return ResultSupreme.Success(isValidAppAgeRating(app, allowedContentRating))
    }

    private suspend fun hasNoContentRatingOnGPlay(app: AppInstall) =
        !verifyContentRatingExists(app)

    private fun isValidAppAgeRating(
        app: AppInstall,
        allowedContentRating: GPlayContentRatingGroup?
    ): Boolean {
        val allowedAgeRatings = allowedContentRating?.ratings?.map { it.lowercase() } ?: emptyList()
        return app.contentRating.id.isNotEmpty() && allowedAgeRatings.contains(app.contentRating.id)
    }

    private fun isParentalControlDisabled(ageGroup: Age) = ageGroup == Age.PARENTAL_CONTROL_DISABLED

    private suspend fun verifyContentRatingExists(app: AppInstall): Boolean {

        if (app.contentRating.id.isEmpty()) {
            gPlayContentRatingRepository.getEnglishContentRating(app.packageName)?.run {
                Timber.d("Updating content rating for package: ${app.packageName}")
                app.contentRating = this
            }
        }

        return app.contentRating.title.isNotEmpty() &&
                app.contentRating.id.isNotEmpty()
    }
}
