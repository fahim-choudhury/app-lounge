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
import foundation.e.apps.data.gitlab.models.EligibleSystemApps
import foundation.e.apps.data.gitlab.models.toApplication
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SystemAppsUpdatesRepository @Inject constructor(
    private val systemAppsUpdatesApi: SystemAppsUpdatesApi,
) {

    suspend fun getAllEligibleApps(): List<EligibleSystemApps>? {
        val response = systemAppsUpdatesApi.getAllEligibleApps()
        if (!response.isSuccessful) return emptyList()
        return response.body()
    }

    suspend fun getSystemAppUpdateInfo(
        packageName: String,
        releaseType: String,
        sdkLevel: Int,
        device: String,
    ): Application? {
        val response = systemAppsUpdatesApi.getSystemAppUpdateInfo(packageName, releaseType)
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
            !updateDef.eligibleAndroidPlatforms.contains(sdkLevel) -> {
                Timber.e("Ineligible sdk level: $packageName, $sdkLevel")
                null
            }
            updateDef.blacklistedDevices.contains(device) -> {
                Timber.e("blacklisted device: $packageName, $device")
                null
            }
            updateDef.blacklistedDevices.contains("${device}@${sdkLevel}") -> {
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