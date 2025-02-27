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

package foundation.e.apps.data.application

import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.install.models.AppInstall

object UpdatesDao {
    private val _appsAwaitingForUpdate: MutableList<Application> = mutableListOf()
    val appsAwaitingForUpdate: List<Application> = _appsAwaitingForUpdate

    private val _successfulUpdatedApps = mutableListOf<AppInstall>()
    val successfulUpdatedApps: List<AppInstall> = _successfulUpdatedApps

    fun addItemsForUpdate(appsNeedUpdate: List<Application>) {
        _appsAwaitingForUpdate.clear()
        _appsAwaitingForUpdate.addAll(appsNeedUpdate)
    }

    fun hasAnyAppsForUpdate() = _appsAwaitingForUpdate.isNotEmpty()

    fun removeUpdateIfExists(packageName: String) = _appsAwaitingForUpdate.removeIf { it.package_name == packageName }

    fun addSuccessfullyUpdatedApp(appInstall: AppInstall) {
        _successfulUpdatedApps.add(appInstall)
    }

    fun clearSuccessfullyUpdatedApps() {
        _successfulUpdatedApps.clear()
    }
}
