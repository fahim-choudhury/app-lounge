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

import com.aurora.gplayapi.data.models.ContentRating
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.parentalcontrol.Age
import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.parentalcontrol.ParentalControlRepository
import foundation.e.apps.data.parentalcontrol.fdroid.FDroidAntiFeatureRepository
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingGroup
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingRepository
import foundation.e.apps.domain.model.ContentRatingValidity
import timber.log.Timber
import javax.inject.Inject

class ValidateAppAgeLimitUseCase @Inject constructor(
    private val gPlayContentRatingRepository: GPlayContentRatingRepository,
    private val fDroidAntiFeatureRepository: FDroidAntiFeatureRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val appsApi: AppsApi,
    private val contentRatingDao: ContentRatingDao,
) {

    companion object {
        const val KEY_ANTI_FEATURES_NSFW = "NSFW"
    }

    private val ageGroup: Age
        get() = parentalControlRepository.getSelectedAgeGroup()

    suspend operator fun invoke(app: AppInstall): ResultSupreme<ContentRatingValidity> {

        return when {
            isParentalControlDisabled() -> ResultSupreme.Success(
                data = ContentRatingValidity(true)
            )

            isGitlabApp(app) -> ResultSupreme.Success(
                data = ContentRatingValidity(true)
            )

            isThirdPartyStoreApp(app) -> ResultSupreme.Success(data = ContentRatingValidity(false))

            isKnownNsfwApp(app) -> ResultSupreme.Success(data = ContentRatingValidity(false))

            isCleanApkApp(app) -> ResultSupreme.Success(
                data = ContentRatingValidity(isValid = !isNsfwAppByCleanApkApi(app))
            )

            isWhiteListedCleanApkApp(app) -> ResultSupreme.Success(
                data = ContentRatingValidity(true)
            )

            hasNoContentRatingOnGPlay(app) -> ResultSupreme.Error()
            else -> validateAgeLimit(ageGroup, app)
        }
    }

    private fun isThirdPartyStoreApp(app: AppInstall): Boolean {
        return blockedAppRepository.isThirdPartyStoreApp(app.packageName)
    }

    private fun isGitlabApp(app: AppInstall): Boolean {
        return app.origin == Origin.GITLAB_RELEASES
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
    ): ResultSupreme<ContentRatingValidity> {
        val allowedContentRating =
            gPlayContentRatingRepository.contentRatingGroups.find { it.id == ageGroup.toString() }

        Timber.d(
            "${app.packageName} - Content rating: ${app.contentRating.id} \n" +
                    "Selected age group: $ageGroup \n" +
                    "Allowed content rating: $allowedContentRating"
        )

        return ResultSupreme.Success(
            ContentRatingValidity(
                isValidAppAgeRating(
                    app,
                    allowedContentRating
                ), app.contentRating
            )
        )
    }

    private suspend fun hasNoContentRatingOnGPlay(app: AppInstall): Boolean {
        return app.origin == Origin.GPLAY && !verifyContentRatingExists(app)
    }

    private fun isValidAppAgeRating(
        app: AppInstall,
        allowedContentRating: GPlayContentRatingGroup?
    ): Boolean {
        val allowedAgeRatings = allowedContentRating?.ratings?.map { it.lowercase() } ?: emptyList()
        return app.contentRating.id.isNotEmpty() && allowedAgeRatings.contains(app.contentRating.id)
    }

    fun isParentalControlDisabled(): Boolean {
        return ageGroup == Age.PARENTAL_CONTROL_DISABLED
    }

    private suspend fun verifyContentRatingExists(app: AppInstall): Boolean {

        if (app.contentRating.id.isEmpty()) {
            val fetchedContentRating =
                gPlayContentRatingRepository.getEnglishContentRating(app.packageName)

            Timber.d("Fetched content rating - ${app.packageName} - ${fetchedContentRating?.id}")

            app.contentRating = if (fetchedContentRating == null) {
                val contentRatingDb = contentRatingDao.getContentRating(app.packageName)
                Timber.d("Content rating from DB - ${app.packageName} - ${contentRatingDb?.ratingId}")
                ContentRating(
                    id = contentRatingDb?.ratingId ?: "",
                    title = contentRatingDb?.ratingTitle ?: "",
                )
            } else fetchedContentRating

        }

        return app.contentRating.title.isNotEmpty() &&
                app.contentRating.id.isNotEmpty()
    }
}
