/*
 *
 *  * Copyright ECORP SAS 2022
 *  * Apps  Quickly and easily install Android apps onto your device!
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.api.faultyApps

import foundation.e.apps.OpenForTesting
import foundation.e.apps.api.fused.data.FusedApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class FaultyAppRepository @Inject constructor(private val faultyAppDao: FaultyAppDao) {

    suspend fun addFaultyApp(packageName: String, error: String) {
        val faultyApp = FaultyApp(packageName, error)
        faultyAppDao.addFaultyApp(faultyApp)
    }

    suspend fun getAllFaultyApps(): List<FaultyApp> {
        return faultyAppDao.getFaultyApps()
    }

    suspend fun deleteFaultyAppByPackageName(packageName: String) {
        faultyAppDao.deleteFaultyAppByPackageName(packageName)
    }

    suspend fun removeFaultyApps(fusedApps: List<FusedApp>): List<FusedApp> {
        val faultyAppsPackageNames = getAllFaultyApps().map { it.packageName }
        return fusedApps.filter { !faultyAppsPackageNames.contains(it.package_name) }
    }
}
