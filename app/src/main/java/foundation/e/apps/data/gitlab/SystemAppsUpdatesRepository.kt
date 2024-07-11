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

import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.gitlab.models.SystemAppInfo
import foundation.e.apps.data.gitlab.models.SystemAppProject
import foundation.e.apps.data.gitlab.models.toApplication
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SystemAppsUpdatesRepository @Inject constructor(
    private val eligibleSystemAppsApi: EligibleSystemAppsApi,
    private val systemAppDefinitionApi: SystemAppDefinitionApi,
) {

    private var systemAppProjectList = mutableListOf<SystemAppProject>()

    suspend fun fetchAllEligibleApps() {
        val response = eligibleSystemAppsApi.getAllEligibleApps()
        if (response.isSuccessful && !response.body().isNullOrEmpty()) {
            response.body()?.let { systemAppProjectList.addAll(it) }
        }
    }

    fun getAllEligibleApps(): List<String> {
        return systemAppProjectList.map { it.packageName }
    }

    suspend fun getSystemAppUpdateInfo(
        packageName: String,
        releaseType: String,
        sdkLevel: Int,
        device: String,
    ): Application? {

        fun isSystemAppBlacklisted(systemAppInfo: SystemAppInfo): Boolean {
            return (systemAppInfo.blacklistedAndroid?.contains(sdkLevel) == true
                    || systemAppInfo.blacklistedDevices?.contains(device) == true
                    || systemAppInfo.blacklistedDevices?.contains("${device}@${sdkLevel}") == true
                    )
        }

        val projectId =
            systemAppProjectList.find { it.packageName == packageName }?.projectId ?: return null

        val response = systemAppDefinitionApi.getSystemAppUpdateInfo(projectId, releaseType)
        if (!response.isSuccessful) {
            Timber.e("Failed to fetch system app update definition for: $packageName, $releaseType")
            return null
        }

        val systemAppInfo = response.body() ?: return null

        if (isSystemAppBlacklisted(systemAppInfo)) {
            Timber.e("Blacklisted system app: $packageName, $systemAppInfo")
            return null
        }

        return systemAppInfo.toApplication()
    }

}