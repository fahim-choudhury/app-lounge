/*
 *  Copyright (C) 2022 MURENA SAS
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.api.fused

import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.database.fusedDownload.FusedDownload

object UpdatesDao {
    private val _appsAwaitingForUpdate: MutableList<FusedApp> = mutableListOf()
    val appsAwaitingForUpdate: List<FusedApp> = _appsAwaitingForUpdate

    private val _successfulUpdatedApps = mutableListOf<FusedDownload>()
    val successfulUpdatedApps: List<FusedDownload> = _successfulUpdatedApps

    fun addItemsForUpdate(appsNeedUpdate: List<FusedApp>) {
        _appsAwaitingForUpdate.clear()
        _appsAwaitingForUpdate.addAll(appsNeedUpdate)
    }

    fun hasAnyAppsForUpdate() = _appsAwaitingForUpdate.isNotEmpty()

    fun removeUpdateIfExists(packageName: String) = _appsAwaitingForUpdate.removeIf { it.package_name == packageName }

    fun addSuccessfullyUpdatedApp(fusedDownload: FusedDownload) {
        _successfulUpdatedApps.add(fusedDownload)
    }

    fun clearSuccessfullyUpdatedApps() {
        _successfulUpdatedApps.clear()
    }
}
