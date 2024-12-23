/*
 * Copyright (C) 2021-2024 MURENA SAS
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

package foundation.e.apps.data.gitlab

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.gitlab.UpdatableSystemAppsApi.*
import foundation.e.apps.data.gitlab.models.OsReleaseType
import foundation.e.apps.data.gitlab.models.SystemAppInfo
import foundation.e.apps.data.gitlab.models.SystemAppProject
import foundation.e.apps.data.gitlab.models.toApplication
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.utils.SystemInfoProvider
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SystemAppsUpdatesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updatableSystemAppsApi: UpdatableSystemAppsApi,
    private val systemAppDefinitionApi: SystemAppDefinitionApi,
    private val applicationDataManager: ApplicationDataManager,
    private val appLoungePackageManager: AppLoungePackageManager,
    private val releaseInfoApi: ReleaseInfoApi,
) {

    private val androidVersionCode by lazy {
        try { getAndroidVersionCodeChar() }
        catch (exception: UnsupportedAndroidApiException) {
            Timber.w(exception.message,
                "Android API isn't in supported range to update some system apps")
            "UnsupportedAndroidAPI"
        }
    }

    private val systemAppProjectList = mutableListOf<SystemAppProject>()

    private fun getUpdatableSystemApps(): List<String> {
        return systemAppProjectList.map { it.packageName }
    }

    suspend fun fetchUpdatableSystemApps(forceRefresh: Boolean = false) {
        val result = handleNetworkResult {
            if (getUpdatableSystemApps().isNotEmpty() && !forceRefresh) {
                return@handleNetworkResult
            }

            val endPoint = getUpdatableSystemAppEndPoint()
            val response = updatableSystemAppsApi.getUpdatableSystemApps(endPoint)

            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                systemAppProjectList.clear()
                response.body()?.let { systemAppProjectList.addAll(it) }
            } else {
                Timber.e("Failed to fetch updatable apps: ${response.errorBody()?.string()}")
            }
        }

        if (!result.isSuccess()) {
            Timber.e("Network error when fetching updatable apps - ${result.message}")
        }
    }

    private fun getUpdatableSystemAppEndPoint(): EndPoint {
        val systemName = getFullSystemName()
        return if (isEligibleToFetchAppListFromTest(systemName)) {
            EndPoint.ENDPOINT_TEST
        } else {
            EndPoint.ENDPOINT_RELEASE
        }
    }

    private fun isEligibleToFetchAppListFromTest(systemName: String) = systemName.isBlank() ||
            systemName.contains("beta") ||
            systemName.contains("rc") ||
            systemName.contains("test")

    private fun isSystemAppBlocked(
        systemAppInfo: SystemAppInfo,
        sdkLevel: Int,
        device: String,
    ): Boolean {
        return systemAppInfo.run {
            sdkLevel < minSdk ||
                    blockedAndroid?.contains(sdkLevel) == true ||
                    blockedDevices?.contains(device) == true ||
                    blockedDevices?.contains("${device}@${sdkLevel}") == true
        }
    }

    private suspend fun getReleaseDetailsUrl(
        systemAppProject: SystemAppProject,
        releaseType: OsReleaseType,
    ): String? {
        val projectId = systemAppProject.projectId
        val releaseResponse = releaseInfoApi.getReleases(projectId)
        var releases = releaseResponse.body()

        if (!releaseResponse.isSuccessful || releases == null) {
            Timber.e("Failed to fetch releases for project id - $projectId")
            return null
        }

        if (systemAppProject.dependsOnAndroidVersion) {
            val versionSuffix = "-$androidVersionCode"
            releases = releases.filter { isVersionedTag(it.tagName, versionSuffix) }
        }

        val sortedReleases = releases.sortedByDescending {
            it.releasedAt
        }

        for (release in sortedReleases) {
            release.getAssetWebLink("${releaseType}.json")?.run {
                return this.removePrefix(SystemAppDefinitionApi.BASE_URL)
            }
        }

        return null
    }

    private fun isVersionedTag(tag: String, versionSuffix: String): Boolean {
        return tag.endsWith(suffix = versionSuffix, ignoreCase = true)
    }

    private suspend fun getApplication(
        packageName: String,
        releaseType: OsReleaseType,
        sdkLevel: Int,
        device: String,
    ): Application? {

        val systemAppProject = systemAppProjectList.find { it.packageName == packageName } ?: return null
        val detailsUrl = getReleaseDetailsUrl(systemAppProject, releaseType) ?: return null

        val systemAppInfo = getSystemAppInfo(packageName, detailsUrl) ?: return null

        return if (isSystemAppBlocked(systemAppInfo, sdkLevel, device)) {
            Timber.e("Blocked system app: $packageName, details: $systemAppInfo")
            null
        } else {
            systemAppInfo.toApplication(context)
        }
    }

    private suspend fun getSystemAppInfo(
        packageName: String,
        detailsUrl: String
    ): SystemAppInfo? {
        val response = systemAppDefinitionApi.getSystemAppUpdateInfo(detailsUrl)

        return if (response.isSuccessful) {
            response.body()
        } else {
            Timber.e("Can't get AppInfo for $packageName, response: ${response.errorBody()?.string()}")
            null
        }
    }

    private fun getFullSystemName(): String {
        return SystemInfoProvider.getSystemProperty(SystemInfoProvider.KEY_LINEAGE_VERSION) ?: ""
    }

    private fun getSdkLevel(): Int {
        return Build.VERSION.SDK_INT
    }

    private fun getDevice(): String {
        return SystemInfoProvider.getSystemProperty(SystemInfoProvider.KEY_LINEAGE_DEVICE) ?: ""
    }

    private fun getSystemReleaseType(): OsReleaseType {
        return SystemInfoProvider.getSystemProperty(SystemInfoProvider.KEY_LINEAGE_RELEASE_TYPE).let {
            OsReleaseType.get(it)
        }
    }

    /**
     * This method must be updated when Murena or /e/ foundation support new Android version
     * or stop to support an old one
     */
    private fun getAndroidVersionCodeChar(): String {
        /* TODO manually add new supported android version when they will be supported.
        * VANILLA_ICE_CREAM (A15) => https://gitlab.e.foundation/e/os/backlog/-/issues/2772
        * BAKLAVA (A16) => https://gitlab.e.foundation/e/os/backlog/-/issues/2773
        */
        return when (val currentAPI = Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2 -> "S"
            Build.VERSION_CODES.TIRAMISU -> "T"
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "U"
            else -> throw UnsupportedAndroidApiException("API level $currentAPI is not supported")
        }
    }

    suspend fun getSystemUpdates(): List<Application> {
        val updateList = mutableListOf<Application>()
        val releaseType = getSystemReleaseType()
        val sdkLevel = getSdkLevel()
        val device = getDevice()

        val updatableApps = getUpdatableSystemApps()
        updatableApps.forEach {

            if (!appLoungePackageManager.isInstalled(it)) {
                // Don't install for system apps which are removed (by root or otherwise)
                return@forEach
            }

            val result = handleNetworkResult {
                getApplication(
                    it,
                    releaseType,
                    sdkLevel,
                    device,
                )
            }

            if (!result.isSuccess()) {
                Timber.e("Failed to get system app info for $it - ${result.message}")
                return@forEach
            }

            val app: Application = result.data ?: return@forEach
            val appStatus = appLoungePackageManager.getPackageStatus(it, app.latest_version_code)
            if (appStatus != Status.UPDATABLE) return@forEach

            app.run {
                applicationDataManager.updateStatus(this)
                updateList.add(this)
                updateSource(context)
            }
        }

        return updateList
    }
}

private class UnsupportedAndroidApiException(message: String) : RuntimeException(message)