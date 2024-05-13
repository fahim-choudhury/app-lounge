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
import foundation.e.apps.data.gitlab.models.ProjectIdMapItem
import foundation.e.apps.data.gitlab.models.toApplication
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SystemAppsUpdatesRepository @Inject constructor(
    private val eligibleSystemAppsApi: EligibleSystemAppsApi,
    private val systemAppDefinitionApi: SystemAppDefinitionApi,
) {

    private var projectIdMap = mutableListOf<ProjectIdMapItem>()

    suspend fun fetchAllEligibleApps() {
        val response = eligibleSystemAppsApi.getAllEligibleApps()
        if (response.isSuccessful && !response.body().isNullOrEmpty()) {
            response.body()?.let { projectIdMap.addAll(it) }
        }
    }

    fun getAllEligibleApps(): List<String> {
        return projectIdMap.map { it.packageName }
    }

    suspend fun getSystemAppUpdateInfo(
        packageName: String,
        releaseType: String,
        sdkLevel: Int,
        device: String,
    ): Application? {

        val projectId =
            projectIdMap.find { it.packageName == packageName }?.projectId ?: return null

        val response = systemAppDefinitionApi.getSystemAppUpdateInfo(projectId, releaseType)
        if (!response.isSuccessful) {
            Timber.e("Failed to fetch system app update definition for: $packageName, $releaseType")
            return null
        }

        val updateDef = response.body()

        return when {
            updateDef == null -> {
                Timber.e("Null update definition: $packageName, $releaseType")
                null
            }
            updateDef.blacklistedAndroid?.contains(sdkLevel) == true -> {
                Timber.e("Ineligible sdk level: $packageName, $sdkLevel")
                null
            }
            updateDef.blacklistedDevices?.contains(device) == true -> {
                Timber.e("blacklisted device: $packageName, $device")
                null
            }
            updateDef.blacklistedDevices?.contains("${device}@${sdkLevel}") == true -> {
                // In case a device on a specific android version is blacklisted.
                // Eg: "redfin@31" would mean Pixel 5 on Android 12 cannot receive this update.
                Timber.e("blacklisted device: $packageName, ${device}@${sdkLevel}")
                null
            }
            else -> {
                val app = updateDef.toApplication()
                app
            }
        }
    }

}