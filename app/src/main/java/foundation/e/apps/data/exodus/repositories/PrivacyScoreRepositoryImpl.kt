/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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
 */

package foundation.e.apps.data.exodus.repositories

import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.di.CommonUtilsModule
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.round

@Singleton
class PrivacyScoreRepositoryImpl @Inject constructor(
    private val blockedAppRepository: BlockedAppRepository
) : PrivacyScoreRepository {

    override fun calculatePrivacyScore(application: Application): Int {
        if (blockedAppRepository.isPrivacyScoreZero(application.package_name)) {
            return 0
        }

        if (application.permsFromExodus == CommonUtilsModule.LIST_OF_NULL) {
            return -1
        }

        val calculateTrackersScore = calculateTrackersScore(application.trackers.size)
        val calculatePermissionsScore = calculatePermissionsScore(
            countAndroidPermissions(application)
        )
        return calculateTrackersScore + calculatePermissionsScore
    }

    private fun calculateTrackersScore(numberOfTrackers: Int): Int {
        return if (numberOfTrackers > THRESHOLD_OF_NON_ZERO_TRACKER_SCORE) MIN_TRACKER_SCORE else MAX_TRACKER_SCORE - numberOfTrackers
    }

    private fun countAndroidPermissions(application: Application) =
        application.permsFromExodus.filter { it.contains("android.permission") }.size

    private fun calculatePermissionsScore(numberOfPermission: Int): Int {
        return if (numberOfPermission > THRESHOLD_OF_NON_ZERO_PERMISSION_SCORE) MIN_PERMISSION_SCORE else round(
            FACTOR_OF_PERMISSION_SCORE * ceil((MAX_PERMISSION_SCORE - numberOfPermission) / DIVIDER_OF_PERMISSION_SCORE)
        ).toInt()
    }

    // please do not put in the top of the class, as it can break the privacy calculation source code link.
    // for more info: https://gitlab.e.foundation/e/os/backlog/-/issues/1353
    companion object {
        private const val MAX_TRACKER_SCORE = 9
        private const val MIN_TRACKER_SCORE = 0
        private const val MAX_PERMISSION_SCORE = 10
        private const val MIN_PERMISSION_SCORE = 0
        private const val THRESHOLD_OF_NON_ZERO_TRACKER_SCORE = 5
        private const val THRESHOLD_OF_NON_ZERO_PERMISSION_SCORE = 9
        private const val FACTOR_OF_PERMISSION_SCORE = 0.2
        private const val DIVIDER_OF_PERMISSION_SCORE = 2.0
    }
}
