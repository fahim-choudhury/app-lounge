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

import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.Allowed
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.Denied
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.DeniedOnDataLoadError
import foundation.e.apps.data.parentalcontrol.gplayrating.GooglePlayContentRatingsRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.domain.parentalcontrol.GetParentalControlStateUseCase
import foundation.e.apps.domain.parentalcontrol.model.ParentalControlState
import foundation.e.apps.domain.parentalcontrol.model.ParentalControlState.AgeGroup
import foundation.e.apps.domain.parentalcontrol.model.ParentalControlState.Disabled
import foundation.e.apps.domain.parentalcontrol.model.isEnabled
import javax.inject.Inject
import javax.inject.Named

class GetAppInstallationPermissionUseCase
@Inject
constructor(
    private val googlePlayContentRatingsRepository: GooglePlayContentRatingsRepository,
    private val getParentalControlStateUseCase: GetParentalControlStateUseCase,
    private val playStoreRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkRepository: CleanApkRepository
) {

    companion object {
        private const val KEY_ANTI_FEATURES_NSFW = "NSFW"
    }

    suspend operator fun invoke(app: AppInstall): AppInstallationPermissionState {
        return when (val parentalControl = getParentalControlStateUseCase.invoke()) {
            is Disabled -> Allowed
            is AgeGroup ->
                when {
                    isFDroidApp(app) -> validateNsfwAntiFeature(app, parentalControl)
                    else -> validateGooglePlayContentRating(app, parentalControl)
                }
        }
    }

    private suspend fun validateNsfwAntiFeature(
        appPendingInstallation: AppInstall,
        parentalControl: ParentalControlState
    ): AppInstallationPermissionState {
        // Need this API call for fetching complete information of F-Droid app.
        // `appPendingInstallation` doesn't have complete data when installation is called from
        // screens such as search, category, etc.
        val app =
            cleanApkRepository
                .getAppDetailsById(appPendingInstallation.id)
                .getOrElse {
                    return DeniedOnDataLoadError
                }
                .app

        return when {
            hasNoAntiFeatures(app) -> Allowed
            isNsfwFDroidApp(app) && parentalControl.isEnabled -> Denied
            else -> Allowed
        }
    }

    private fun hasNoAntiFeatures(app: Application) = app.antiFeatures.isEmpty()

    private fun isNsfwFDroidApp(app: Application) =
        app.antiFeatures.any { antiFeature -> antiFeature.containsKey(KEY_ANTI_FEATURES_NSFW) }

    private suspend fun validateGooglePlayContentRating(
        app: AppInstall,
        parentalControlState: AgeGroup
    ): AppInstallationPermissionState {

        return when {
            isGooglePlayApp(app) && hasNoContentRating(app) -> DeniedOnDataLoadError
            hasValidContentRating(app, parentalControlState) -> Allowed
            else -> Denied
        }
    }

    private fun isGooglePlayApp(app: AppInstall): Boolean {
        return !isFDroidApp(app) && app.type != Type.PWA
    }

    private fun isFDroidApp(app: AppInstall): Boolean {
        return app.isFDroidApp
    }

    private suspend fun hasNoContentRating(app: AppInstall): Boolean {
        return !verifyContentRatingExists(app)
    }

    private fun hasValidContentRating(
        app: AppInstall,
        parentalControlState: AgeGroup,
    ): Boolean {
        return when {
            app.contentRating.id.isBlank() -> false
            else -> {
                val allowedContentRatingGroup =
                    googlePlayContentRatingsRepository.contentRatingGroups.find {
                        it.id == parentalControlState.ageGroup.name
                    } ?: return false

                allowedContentRatingGroup.ratings.contains(app.contentRating.id)
            }
        }
    }

    private suspend fun verifyContentRatingExists(app: AppInstall): Boolean {
        if (app.contentRating.title.isEmpty() || app.contentRating.id.isEmpty()) {
            app.contentRating = playStoreRepository.getEnglishContentRating(app.packageName)
        }

        return app.contentRating.title.isNotEmpty() && app.contentRating.id.isNotEmpty()
    }
}
