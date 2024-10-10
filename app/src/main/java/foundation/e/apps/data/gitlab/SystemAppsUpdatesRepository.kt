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
import foundation.e.apps.data.gitlab.models.GitlabReleaseInfo
import foundation.e.apps.data.gitlab.models.SystemAppInfo
import foundation.e.apps.data.gitlab.models.SystemAppProject
import foundation.e.apps.data.gitlab.models.toApplication
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.utils.SystemInfoProvider
import retrofit2.Response
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
) {
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

    private fun getUpdatableSystemAppEndPoint(): UpdatableSystemAppsApi.EndPoint {
        val systemName = getFullSystemName()
        return if (
            systemName.isBlank() ||
            systemName.contains("beta") ||
            systemName.contains("rc")
        ) {
            UpdatableSystemAppsApi.EndPoint.ENDPOINT_TEST
        } else {
            UpdatableSystemAppsApi.EndPoint.ENDPOINT_RELEASE
        }
    }

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

    private suspend fun getSystemAppUpdateInfo(
        packageName: String,
        releaseType: String,
        sdkLevel: Int,
        device: String,
    ): Application? {

        val systemAppProject = systemAppProjectList.find { it.packageName == packageName } ?: return null


        val response = getSystemAppInfo(systemAppProject, releaseType)
        if (response == null) { //todo refactor to avoid checking this
            Timber.e("Can't get latest release for : $packageName")
            return null
        }
        val systemAppInfo = response?.body()

        return if (systemAppInfo == null) {
            Timber.e("Null app info for: $packageName, response: ${response.errorBody()?.string()}")
            null
        } else if (isSystemAppBlocked(systemAppInfo, sdkLevel, device)) {
            Timber.e("Blocked system app: $packageName, details: $systemAppInfo")
            null
        } else {
            systemAppInfo.toApplication(context)
        }
    }

    private suspend fun getSystemAppInfo(systemAppProject: SystemAppProject, releaseType: String): Response<SystemAppInfo>? {
        val projectId = systemAppProject.projectId

        return if (systemAppProject.dependsOnAndroidVersion) {
            val latestRelease = getLatestSystemAppReleaseByAndroidVersion(projectId)
            if (latestRelease == null) {
                null //todo replace by an error code to avoid to check for nullity in calling method ?
            } else {
                val releaseTag = latestRelease.tagName
                systemAppDefinitionApi.getSystemAppUpdateInfoByTag(projectId, releaseTag, releaseType)
            }

        } else {
            systemAppDefinitionApi.getLatestSystemAppUpdateInfo(projectId, releaseType)
        }
    }

    //todo: rename & rewrite ?
    private suspend fun getLatestSystemAppReleaseByAndroidVersion(projectId: Int): GitlabReleaseInfo? {
        val gitlabReleaseList = systemAppDefinitionApi.getSystemAppReleases(projectId).body()

        val latestRelease = gitlabReleaseList?.filter {
            it.tagName.contains("api${getAndroidVersion()}-")
        }?.sortedByDescending { it.releasedAt }?.first()

        return latestRelease
    }

    /*
    todo: this method cannot match upper version. UPSIDE_DOWN_CAKE or VANILLA or note available
    through BUILD.VERSIO_CODES (may be due to targeted SDK or minimum SDK.)
    todo: This method shouldn't be called for each app. We need to define it only once!
     */
    private fun getAndroidVersion(): String {
        return when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.Q -> "Q"
            Build.VERSION_CODES.R -> "R"
            Build.VERSION_CODES.S -> "S"
            Build.VERSION_CODES.TIRAMISU -> "T"
            else -> "unknown"
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

    private fun getSystemReleaseType(): String {
        return SystemInfoProvider.getSystemProperty(SystemInfoProvider.KEY_LINEAGE_RELEASE_TYPE) ?: ""
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
                getSystemAppUpdateInfo(
                    it,
                    releaseType,
                    sdkLevel,
                    device,
                )
            }

            result.data?.run {
                applicationDataManager.updateStatus(this)
                updateList.add(this)
                updateSource(context)
            }

            if (!result.isSuccess()) {
                Timber.e("Failed to get system app info for $it - ${result.message}")
            }
        }

        return updateList
    }

}
