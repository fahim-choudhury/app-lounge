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

import android.content.Context
import android.content.pm.ApplicationInfo
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.api.faultyApps.FaultyAppRepository
import foundation.e.apps.api.fused.FusedAPIImpl.Companion.APP_TYPE_ANY
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.Constants
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.isUnFiltered
import foundation.e.apps.utils.modules.PreferenceManagerModule
import javax.inject.Inject

class UpdatesManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pkgManagerModule: PkgManagerModule,
    private val fusedAPIRepository: FusedAPIRepository,
    private val faultyAppRepository: FaultyAppRepository,
    private val preferenceManagerModule: PreferenceManagerModule,
) {

    companion object {
        const val PACKAGE_NAME_F_DROID = "org.fdroid.fdroid"
        const val PACKAGE_NAME_F_DROID_PRIVILEGED = "org.fdroid.fdroid.privileged"
        const val PACKAGE_NAME_ANDROID_VENDING = "com.android.vending"
    }

    private val TAG = UpdatesManagerImpl::class.java.simpleName

    private val userApplications: List<ApplicationInfo>
        get() = pkgManagerModule.getAllUserApps()

    suspend fun getUpdates(authData: AuthData): Pair<List<FusedApp>, ResultStatus> {
        val updateList = mutableListOf<FusedApp>()
        var status = ResultStatus.OK

        val openSourceInstalledApps = getOpenSourceInstalledApps().toMutableList()
        val gPlayInstalledApps = getGPlayInstalledApps().toMutableList()

        val otherStoreApps = getAppsFromOtherStores()

        if (preferenceManagerModule.shouldUpdateAppsFromOtherStores()) {
            openSourceInstalledApps.addAll(otherStoreApps)
        }

        // Get open source app updates
        if (openSourceInstalledApps.isNotEmpty()) {
            status = getUpdatesFromApi({
                fusedAPIRepository.getApplicationDetails(
                    openSourceInstalledApps,
                    authData,
                    Origin.CLEANAPK
                )
            }, updateList)
        }

        if (preferenceManagerModule.shouldUpdateAppsFromOtherStores()) {
            val updateListFromFDroid = updateList.map { it.package_name }
            val otherStoreAppsForGPlay = otherStoreApps - updateListFromFDroid.toSet()
            gPlayInstalledApps.addAll(otherStoreAppsForGPlay)
        }

        // Get GPlay app updates
        if (getApplicationCategoryPreference().contains(APP_TYPE_ANY) &&
            gPlayInstalledApps.isNotEmpty()) {

            status = getUpdatesFromApi({
                fusedAPIRepository.getApplicationDetails(
                    gPlayInstalledApps,
                    authData,
                    Origin.GPLAY
                )
            }, updateList)
        }

        val nonFaultyUpdateList = faultyAppRepository.removeFaultyApps(updateList)
        return Pair(nonFaultyUpdateList, status)
    }

    suspend fun getUpdatesOSS(): Pair<List<FusedApp>, ResultStatus> {
        val updateList = mutableListOf<FusedApp>()
        var status = ResultStatus.OK

        val openSourceInstalledApps = getOpenSourceInstalledApps().toMutableList()

        val otherStoreApps = getAppsFromOtherStores()

        if (preferenceManagerModule.shouldUpdateAppsFromOtherStores()) {
            openSourceInstalledApps.addAll(otherStoreApps)
        }

        if (openSourceInstalledApps.isNotEmpty()) {
            status = getUpdatesFromApi({
                fusedAPIRepository.getApplicationDetails(
                    openSourceInstalledApps,
                    AuthData("", ""),
                    Origin.CLEANAPK
                )
            }, updateList)
        }

        val nonFaultyUpdateList = faultyAppRepository.removeFaultyApps(updateList)
        return Pair(nonFaultyUpdateList, status)
    }

    /**
     * Lists apps directly updatable by App Lounge from the Open Source category.
     * (This includes apps installed by F-Droid client app, if used by the user;
     * F-Droid is not considered a third party source.)
     */
    private fun getOpenSourceInstalledApps(): List<String> {
        return userApplications.filter {
            pkgManagerModule.getInstallerName(it.packageName) in listOf(
                context.packageName,
                PACKAGE_NAME_F_DROID,
                PACKAGE_NAME_F_DROID_PRIVILEGED,
            )
        }.map { it.packageName }
    }

    /**
     * Lists GPlay apps directly updatable by App Lounge.
     *
     * GPlay apps installed by App Lounge alone can have their installer package
     * set as "com.android.vending".
     */
    private fun getGPlayInstalledApps(): List<String> {
        return userApplications.filter {
            pkgManagerModule.getInstallerName(it.packageName) in listOf(
                PACKAGE_NAME_ANDROID_VENDING,
            )
        }.map { it.packageName }
    }

    /**
     * Lists apps installed from other app stores.
     * (F-Droid client is not considered a third party source.)
     *
     * @return List of package names of apps installed from other app stores like
     * Aurora Store, Apk mirror, apps installed from browser, apps from ADB etc.
     */
    private fun getAppsFromOtherStores(): List<String> {
        return userApplications.filter {
            it.packageName !in (getGPlayInstalledApps() + getOpenSourceInstalledApps())
        }.map { it.packageName }
    }

    /**
     * Runs API (GPlay api or CleanApk) and accumulates the updatable apps
     * into a provided list.
     *
     * @param apiFunction Function that calls an API method to fetch update information.
     * Apps returned is filtered to get only the apps which can be downloaded and updated.
     * @param updateAccumulationList A list into which the filtered results from
     * [apiFunction] is stored. The caller needs to read this list to get the update info.
     *
     * @return ResultStatus from calling [apiFunction].
     */
    private suspend fun getUpdatesFromApi(
        apiFunction: suspend () -> Pair<List<FusedApp>, ResultStatus>,
        updateAccumulationList: MutableList<FusedApp>,
    ): ResultStatus {
        val apiResult = apiFunction()
        val updatableApps = apiResult.first.filter {
            it.status == Status.UPDATABLE && it.filterLevel.isUnFiltered()
        }
        updateAccumulationList.addAll(updatableApps)
        return apiResult.second
    }

    fun getApplicationCategoryPreference(): List<String> {
        return fusedAPIRepository.getApplicationCategoryPreference()
    }
}
