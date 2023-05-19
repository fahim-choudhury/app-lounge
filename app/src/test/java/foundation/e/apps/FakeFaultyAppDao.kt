/*
 *  Copyright (C) 2022  MURENA SAS
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

package foundation.e.apps

import foundation.e.apps.data.faultyApps.FaultyApp
import foundation.e.apps.data.faultyApps.FaultyAppDao

class FakeFaultyAppDao : FaultyAppDao {

    private val faultyAppList = mutableListOf<FaultyApp>()

    override suspend fun addFaultyApp(faultyApp: FaultyApp): Long {
        faultyAppList.add(faultyApp)
        return -1
    }

    override suspend fun getFaultyApps(): List<FaultyApp> {
        return faultyAppList
    }

    override suspend fun deleteFaultyAppByPackageName(packageName: String): Int {
        faultyAppList.removeIf { it.packageName == packageName }
        return -1
    }
}
