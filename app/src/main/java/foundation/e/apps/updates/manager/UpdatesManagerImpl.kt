/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.updates.manager

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.faultyApps.FaultyAppRepository
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.isUnFiltered
import javax.inject.Inject

class UpdatesManagerImpl @Inject constructor(
    private val pkgManagerModule: PkgManagerModule,
    private val fusedAPIRepository: FusedAPIRepository,
    private val faultyAppRepository: FaultyAppRepository
) {
    private val TAG = UpdatesManagerImpl::class.java.simpleName

    // TODO: MAKE THIS LOGIC MORE SANE
    suspend fun getUpdates(authData: AuthData): ResultSupreme<List<FusedApp>> {
        val pkgList = mutableListOf<String>()
        val updateList = mutableListOf<FusedApp>()
        var status = ResultStatus.OK
        var cleanapkFailed = false

        val userApplications = pkgManagerModule.getAllUserApps()
        userApplications.forEach { pkgList.add(it.packageName) }

        if (pkgList.isNotEmpty()) {
            // Get updates from CleanAPK
            val cleanAPKResult = fusedAPIRepository.getApplicationDetails(
                pkgList,
                authData,
                Origin.CLEANAPK
            )
            cleanAPKResult.first.forEach {
                if (it.package_name in pkgList) pkgList.remove(it.package_name)
                if (it.status == Status.UPDATABLE && it.filterLevel.isUnFiltered()) updateList.add(it)
            }
            cleanAPKResult.second.let {
                if (it != ResultStatus.OK) {
                    status = it
                    cleanapkFailed = true
                }
            }

            // Check for remaining apps from GPlay
            val gPlayResult = fusedAPIRepository.getApplicationDetails(
                pkgList,
                authData,
                Origin.GPLAY
            )
            gPlayResult.first.forEach {
                if (it.status == Status.UPDATABLE && it.filterLevel.isUnFiltered()) updateList.add(it)
            }
            gPlayResult.second.let {
                if (it != ResultStatus.OK) {
                    status = it
                }
            }
        }

        return ResultSupreme.create(status, getNonFaultyApps(updateList), cleanapkFailed.toString())
    }

    /*
     * Get updates only from cleanapk.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413 [2]
     */
    suspend fun getUpdatesOSS(): ResultSupreme<List<FusedApp>> {
        val updateList = mutableListOf<FusedApp>()
        val pkgList = pkgManagerModule.getAllUserApps().map { it.packageName }

        return if (pkgList.isNotEmpty()) {
            fusedAPIRepository.getApplicationDetailsOSS(pkgList).run {
                this.data?.forEach {
                    if (it.status == Status.UPDATABLE) updateList.add(it)
                }
                ResultSupreme.replicate(this, getNonFaultyApps(updateList))
            }
        } else {
            ResultSupreme.Success(listOf())
        }
    }

    private suspend fun getNonFaultyApps(list: List<FusedApp>): List<FusedApp> {
        val faultyAppsPackageNames = faultyAppRepository.getAllFaultyApps().map { it.packageName }
        return list.filter { !faultyAppsPackageNames.contains(it.package_name) }
    }

    fun getApplicationCategoryPreference(): String {
        return fusedAPIRepository.getApplicationCategoryPreference()
    }
}
