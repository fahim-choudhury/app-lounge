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

import foundation.e.apps.data.Result
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.exodus.ExodusTrackerApi
import foundation.e.apps.data.exodus.Report
import foundation.e.apps.data.exodus.Tracker
import foundation.e.apps.data.exodus.TrackerDao
import foundation.e.apps.data.exodus.models.AppPrivacyInfo
import foundation.e.apps.data.fused.data.Application
import foundation.e.apps.data.getResult
import foundation.e.apps.di.CommonUtilsModule.LIST_OF_NULL
import foundation.e.apps.utils.getFormattedString
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrivacyInfoRepositoryImpl @Inject constructor(
    private val exodusTrackerApi: ExodusTrackerApi,
    private val trackerDao: TrackerDao
) : IAppPrivacyInfoRepository {
    companion object {
        private const val DATE_FORMAT = "ddMMyyyy"
        private const val SOURCE_FDROID = "fdroid"
        private const val SOURCE_GOOGLE = "google"
    }

    private var trackers: List<Tracker> = listOf()

    override suspend fun getAppPrivacyInfo(
        application: Application,
        appHandle: String
    ): Result<AppPrivacyInfo> {
        if (application.trackers.isNotEmpty() && application.permsFromExodus.isNotEmpty()) {
            val appInfo = AppPrivacyInfo(application.trackers, application.permsFromExodus, application.reportId)
            return Result.success(appInfo)
        }

        val appTrackerInfoResult = getResult {
            exodusTrackerApi.getTrackerInfoOfApp(
                appHandle,
                application.latest_version_code,
            )
        }

        if (appTrackerInfoResult.isSuccess()) {
            return parsePrivacyInfo(application, appTrackerInfoResult)
        }
        return Result.error(extractErrorMessage(appTrackerInfoResult))
    }

    private suspend fun parsePrivacyInfo(
        application: Application,
        appTrackerInfoResult: Result<List<Report>>
    ): Result<AppPrivacyInfo> {
        val appPrivacyPrivacyInfoResult =
            handleAppPrivacyInfoResultSuccess(application, appTrackerInfoResult)

        updateFusedApp(application, appPrivacyPrivacyInfoResult)
        return appPrivacyPrivacyInfoResult
    }

    private fun updateFusedApp(
        application: Application,
        appPrivacyPrivacyInfoResult: Result<AppPrivacyInfo>
    ) {
        application.trackers = appPrivacyPrivacyInfoResult.data?.trackerList ?: LIST_OF_NULL
        application.permsFromExodus = appPrivacyPrivacyInfoResult.data?.permissionList ?: LIST_OF_NULL
        application.reportId = appPrivacyPrivacyInfoResult.data?.reportId ?: -1L
        if (application.permsFromExodus != LIST_OF_NULL) {
            application.perms = application.permsFromExodus
        }
    }

    private suspend fun handleAppPrivacyInfoResultSuccess(
        application: Application,
        appTrackerResult: Result<List<Report>>,
    ): Result<AppPrivacyInfo> {
        if (trackers.isEmpty()) {
            generateTrackerList()
        }
        return createAppPrivacyInfo(application, appTrackerResult)
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
        val date = Date().getFormattedString(DATE_FORMAT, Locale("en"))
        val result = getResult { exodusTrackerApi.getTrackerList(date) }
        if (result.isSuccess()) {
            result.data?.let {
                val trackerList = it.trackers.values.toList()
                trackerDao.saveTrackers(trackerList)
                this.trackers = trackerList
            }
        }
    }

    private fun extractErrorMessage(appTrackerResult: Result<List<Report>>): String {
        return appTrackerResult.message ?: "Unknown Error"
    }

    private fun createAppPrivacyInfo(
        application: Application,
        appTrackerResult: Result<List<Report>>,
    ): Result<AppPrivacyInfo> {
        appTrackerResult.data?.let {
            return Result.success(getAppPrivacyInfo(application, it))
        }
        return Result.error(extractErrorMessage(appTrackerResult))
    }

    private fun getAppPrivacyInfo(
        application: Application,
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

        val latestTrackerData = getLatestTrackerData(application, appTrackerData)
            ?: return AppPrivacyInfo(LIST_OF_NULL, LIST_OF_NULL)

        val appTrackers = extractAppTrackers(latestTrackerData)
        val permissions = latestTrackerData.permissions
        return AppPrivacyInfo(appTrackers, permissions, latestTrackerData.report)
    }

    private fun getLatestTrackerData(
        application: Application,
        appTrackerData: List<Report>
    ): Report? {
        val source = if (application.origin == Origin.CLEANAPK) SOURCE_FDROID else SOURCE_GOOGLE
        val filteredAppTrackerData = appTrackerData.filter { it.source == source }
        if (filteredAppTrackerData.isEmpty()) {
            return null
        }

        val sortedTrackerData =
            filteredAppTrackerData.sortedByDescending { trackerData -> trackerData.versionCode.toLong() }
        return sortedTrackerData[0]
    }

    private fun extractAppTrackers(latestTrackerData: Report): List<String> {
        return trackers.filter {
            latestTrackerData.trackers.contains(it.id)
        }.map { it.name }
    }
}
