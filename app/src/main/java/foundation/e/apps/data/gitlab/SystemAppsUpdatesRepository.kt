/*
 * Copyright (C) 2019-2023  E FOUNDATION
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
    private val eligibleSystemAppsApi: EligibleSystemAppsApi,
    private val systemAppDefinitionApi: SystemAppDefinitionApi,
    private val applicationDataManager: ApplicationDataManager,
    private val appLoungePackageManager: AppLoungePackageManager,
) {

    private var systemAppProjectList = mutableListOf<SystemAppProject>()

    suspend fun fetchAllEligibleApps() {
        val result = handleNetworkResult {
            val response = eligibleSystemAppsApi.getAllEligibleApps()
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                response.body()?.let { systemAppProjectList.addAll(it) }
            } else {
                Timber.e("Failed to fetch eligible apps: ${response.errorBody()?.string()}")
            }
        }

        if (!result.isSuccess()) {
            Timber.e("Network error when fetching eligible apps - ${result.message}")
        }
    }

    fun getAllEligibleApps(): List<String> {
        return systemAppProjectList.map { it.packageName }
    }

    private fun isSystemAppBlacklisted(
        systemAppInfo: SystemAppInfo,
        sdkLevel: Int,
        device: String,
    ): Boolean {
        return systemAppInfo.run {
            sdkLevel < minSdk ||
                    blacklistedAndroid?.contains(sdkLevel) == true ||
                    blacklistedDevices?.contains(device) == true ||
                    blacklistedDevices?.contains("${device}@${sdkLevel}") == true
        }
    }

    private suspend fun getSystemAppUpdateInfo(
        packageName: String,
        releaseType: String,
        sdkLevel: Int,
        device: String,
    ): Application? {

        val projectId =
            systemAppProjectList.find { it.packageName == packageName }?.projectId ?: return null

        val response = systemAppDefinitionApi.getSystemAppUpdateInfo(projectId, releaseType)
        val systemAppInfo = response.body()

        return if (systemAppInfo == null) {
            Timber.e("Null app info for: $packageName, response: ${response.errorBody()?.string()}")
            null
        } else if (isSystemAppBlacklisted(systemAppInfo, sdkLevel, device)) {
            Timber.e("Blacklisted system app: $packageName, details: $systemAppInfo")
            null
        } else {
            systemAppInfo.toApplication()
        }
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

        val eligibleApps = getAllEligibleApps()
        eligibleApps.forEach {

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
