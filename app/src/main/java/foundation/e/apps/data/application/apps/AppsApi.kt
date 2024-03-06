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

package foundation.e.apps.data.application.apps

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status

interface AppsApi {

    /*
     * Function to search cleanapk using package name.
     * Will be used to handle f-droid deeplink.
     */
    suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus>

    suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<Application>, ResultStatus>

    suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<Application, ResultStatus>

    /**
     * Get fused app installation status.
     * Applicable for both native apps and PWAs.
     *
     * Recommended to use this instead of [PkgManagerModule.getPackageStatus].
     */
    fun getFusedAppInstallationStatus(application: Application): Status

    suspend fun getAppFilterLevel(application: Application, authData: AuthData?): FilterLevel

    /**
     * @return returns true if there is changes in data, otherwise false
     */
    fun isAnyFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean

    fun isAnyAppInstallStatusChanged(currentList: List<Application>): Boolean
    fun isOpenSourceSelected(): Boolean

    suspend fun getSystemUpdates(): List<Application>
}
