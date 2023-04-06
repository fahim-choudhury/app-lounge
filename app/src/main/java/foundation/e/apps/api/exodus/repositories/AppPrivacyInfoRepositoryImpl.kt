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

package foundation.e.apps.api.exodus.repositories

import foundation.e.apps.api.Result
import foundation.e.apps.api.exodus.ExodusTrackerApi
import foundation.e.apps.api.exodus.Report
import foundation.e.apps.api.exodus.Tracker
import foundation.e.apps.api.exodus.TrackerDao
import foundation.e.apps.api.exodus.models.AppPrivacyInfo
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.getResult
import foundation.e.apps.utils.modules.CommonUtilsModule.LIST_OF_NULL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.round

@Singleton
class AppPrivacyInfoRepositoryImpl @Inject constructor(
    private val exodusTrackerApi: ExodusTrackerApi,
    private val trackerDao: TrackerDao
) : IAppPrivacyInfoRepository {

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

    private var trackers: List<Tracker> = listOf()

    override suspend fun getAppPrivacyInfo(
        fusedApp: FusedApp,
        appHandle: String
    ): Result<AppPrivacyInfo> {
        if (fusedApp.trackers.isNotEmpty() && fusedApp.permsFromExodus.isNotEmpty()) {
            val appInfo = AppPrivacyInfo(fusedApp.trackers, fusedApp.permsFromExodus)
            return Result.success(appInfo)
        }

        val appTrackerInfoResult = getResult {
            exodusTrackerApi.getTrackerInfoOfApp(
                appHandle,
                fusedApp.latest_version_code,
            )
        }
        if (appTrackerInfoResult.isSuccess()) {
            val appPrivacyPrivacyInfoResult =
                handleAppPrivacyInfoResultSuccess(appTrackerInfoResult)
            updateFusedApp(fusedApp, appPrivacyPrivacyInfoResult)
            return appPrivacyPrivacyInfoResult
        }
        return Result.error(extractErrorMessage(appTrackerInfoResult))
    }

    private fun updateFusedApp(
        fusedApp: FusedApp,
        appPrivacyPrivacyInfoResult: Result<AppPrivacyInfo>
    ) {
        fusedApp.trackers = appPrivacyPrivacyInfoResult.data?.trackerList ?: LIST_OF_NULL
        fusedApp.permsFromExodus = appPrivacyPrivacyInfoResult.data?.permissionList ?: LIST_OF_NULL
        if (fusedApp.permsFromExodus != LIST_OF_NULL) {
            fusedApp.perms = fusedApp.permsFromExodus
        }
    }

    private suspend fun handleAppPrivacyInfoResultSuccess(
        appTrackerResult: Result<List<Report>>,
    ): Result<AppPrivacyInfo> {
        if (trackers.isEmpty()) {
            generateTrackerList()
        }
        return createAppPrivacyInfoResult(appTrackerResult)
    }

    private suspend fun generateTrackerList() {
        val trackerListOfLocalDB = trackerDao.getTrackers()
        if (trackerListOfLocalDB.isNotEmpty()) {
            this.trackers = trackerListOfLocalDB
        } else {
            generateTrackerListFromExodusApi()
        }
    }

    private suspend fun generateTrackerListFromExodusApi() {
        val result = getResult { exodusTrackerApi.getTrackerList(getDate()) }
        if (result.isSuccess()) {
            result.data?.let {
                val trackerList = it.trackers.values.toList()
                trackerDao.saveTrackers(trackerList)
                this.trackers = trackerList
            }
        }
    }

    private fun getDate(): String {
        val dateFormat = SimpleDateFormat("ddMMyyyy", Locale("en"))
        return dateFormat.format(Date())
    }

    private fun extractErrorMessage(appTrackerResult: Result<List<Report>>): String {
        return appTrackerResult.message ?: "Unknown Error"
    }

    private fun createAppPrivacyInfoResult(
        appTrackerResult: Result<List<Report>>,
    ): Result<AppPrivacyInfo> {
        appTrackerResult.data?.let {
            return Result.success(getAppPrivacyInfo(it))
        }
        return Result.error(extractErrorMessage(appTrackerResult))
    }

    private fun getAppPrivacyInfo(
        appTrackerData: List<Report>,
    ): AppPrivacyInfo {
        /*
         * If the response is empty, that means there is no data on Exodus API about this app,
         * i.e. invalid data.
         * We signal this by list of "null".
         * It is not enough to send just empty lists, as an app can actually have zero trackers
         * and zero permissions. This is not to be confused with invalid data.
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5136
         */
        if (appTrackerData.isEmpty()) {
            return AppPrivacyInfo(LIST_OF_NULL, LIST_OF_NULL)
        }
        val sortedTrackerData =
            appTrackerData.sortedByDescending { trackerData -> trackerData.versionCode.toLong() }
        val appTrackers = extractAppTrackers(sortedTrackerData)
        val permissions = sortedTrackerData[0].permissions
        return AppPrivacyInfo(appTrackers, permissions)
    }

    private fun extractAppTrackers(sortedTrackerData: List<Report>): List<String> {
        return trackers.filter {
            sortedTrackerData[0].trackers.contains(it.id)
        }.map { it.name }
    }

    override fun calculatePrivacyScore(fusedApp: FusedApp): Int {
        if (fusedApp.permsFromExodus == LIST_OF_NULL) {
            return -1
        }
        val calculateTrackersScore = calculateTrackersScore(fusedApp.trackers.size)
        val calculatePermissionsScore = calculatePermissionsScore(
            countAndroidPermissions(fusedApp)
        )
        return calculateTrackersScore + calculatePermissionsScore
    }

    private fun countAndroidPermissions(fusedApp: FusedApp) =
        fusedApp.permsFromExodus.filter { it.contains("android.permission") }.size

    private fun calculateTrackersScore(numberOfTrackers: Int): Int {
        return if (numberOfTrackers > THRESHOLD_OF_NON_ZERO_TRACKER_SCORE) MIN_TRACKER_SCORE else MAX_TRACKER_SCORE - numberOfTrackers
    }

    private fun calculatePermissionsScore(numberOfPermission: Int): Int {
        return if (numberOfPermission > THRESHOLD_OF_NON_ZERO_PERMISSION_SCORE) MIN_PERMISSION_SCORE else round(
            FACTOR_OF_PERMISSION_SCORE * ceil((MAX_PERMISSION_SCORE - numberOfPermission) / DIVIDER_OF_PERMISSION_SCORE)
        ).toInt()
    }
}
