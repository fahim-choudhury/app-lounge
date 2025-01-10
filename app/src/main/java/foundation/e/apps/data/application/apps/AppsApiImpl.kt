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

import foundation.e.apps.data.Stores
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.ui.applicationlist.ApplicationDiffUtil
import javax.inject.Inject
import foundation.e.apps.data.enums.Source

class AppsApiImpl @Inject constructor(
    private val appLoungePreference: AppLoungePreference,
    private val stores: Stores,
    private val applicationDataManager: ApplicationDataManager
) : AppsApi {

    override suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus> {
        var application = Application()
        val result = handleNetworkResult {
            application = stores.getStores()[Source.OPEN_SOURCE]?.getAppDetails(packageName) ?: Application()
            application.source = Source.OPEN_SOURCE
            application.updateType()
            application.updateFilterLevel()
        }

        return Pair(application, result.getResultStatus())
    }

    /*
     * Handy method to run on an instance of FusedApp to update its filter level.
     */
    private fun Application.updateFilterLevel() {
        this.filterLevel = applicationDataManager.getAppFilterLevel(this)
    }

    override suspend fun getApplicationDetails(
        packageNameList: List<String>,
        source: Source
    ): Pair<List<Application>, ResultStatus> {
        val list = mutableListOf<Application>()

        val response: Pair<List<Application>, ResultStatus> =
            if (source == Source.OPEN_SOURCE || source == Source.PWA) {
                getAppDetailsListFromCleanApk(packageNameList)
            } else {
                getAppDetailsListFromGPlay(packageNameList)
            }

        response.first.forEach {
            if (it.package_name.isNotBlank()) {
                applicationDataManager.updateStatus(it)
                it.updateType()
                list.add(it)
            }
        }

        return Pair(list, response.second)
    }

    /*
     * Get app details of a list of apps from cleanapk.
     * Returns list of FusedApp and ResultStatus - which will reflect error if even one app fails.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun getAppDetailsListFromCleanApk(
        packageNameList: List<String>,
    ): Pair<List<Application>, ResultStatus> {
        val status = ResultStatus.OK
        val applicationList = mutableListOf<Application>()

        for (packageName in packageNameList) {
            applicationList.add(stores.getStores()[Source.OPEN_SOURCE]?.getAppDetails(packageName) ?: Application())
        }

        return Pair(applicationList, status)
    }

    private suspend fun getAppDetailsListFromGPlay(
        packageNameList: List<String>,
    ): Pair<List<Application>, ResultStatus> {
        val applicationList = mutableListOf<Application>()

        for (packageName in packageNameList) {
            val app = stores.getStores()[Source.PLAY_STORE]?.getAppDetails(packageName) ?: Application()
            handleFilteredApps(app, applicationList)
        }

        return Pair(applicationList, ResultStatus.OK)
    }

    /*
     * Some apps are restricted to locations. Example "com.skype.m2".
     * For restricted apps, check if it is possible to get their specific app info.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
     */
    private fun handleFilteredApps(
        app: Application,
        applicationList: MutableList<Application>
    ) {
        val filter = applicationDataManager.getAppFilterLevel(app)
        if (filter.isUnFiltered()) {
            applicationList.add(
                app.apply {
                    filterLevel = filter
                }
            )
        }
    }

    override suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        source: Source
    ): Pair<Application, ResultStatus> {
        var application: Application

        val result = handleNetworkResult {

            val store = stores.getStores()[source]
                ?: throw IllegalStateException("Could not get store")

            application = store.getAppDetails(packageName)
            application.let {
                applicationDataManager.updateStatus(it)
                it.source = source
                it.updateType()
                it.updateFilterLevel()
            }
            application
        }

        return Pair(result.data ?: Application(), result.getResultStatus())
    }

    override fun getFusedAppInstallationStatus(application: Application): Status {
        return applicationDataManager.getFusedAppInstallationStatus(application)
    }

    override suspend fun getAppFilterLevel(
        application: Application,
    ): FilterLevel {
        return applicationDataManager.getAppFilterLevel(application)
    }

    override fun isAnyFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        if (newApplications.size != oldApplications.size) {
            return true
        }

        return areApplicationsChanged(newApplications, oldApplications)
    }

    private fun areApplicationsChanged(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        val fusedAppDiffUtil = ApplicationDiffUtil()
        newApplications.forEach {
            val indexOfNewFusedApp = newApplications.indexOf(it)
            if (!fusedAppDiffUtil.areContentsTheSame(it, oldApplications[indexOfNewFusedApp])) {
                return true
            }
        }

        return false
    }

    override fun isAnyAppInstallStatusChanged(currentList: List<Application>): Boolean {
        currentList.forEach {
            if (it.status == Status.INSTALLATION_ISSUE) {
                return@forEach
            }

            val currentAppStatus = getFusedAppInstallationStatus(it)
            if (it.status != currentAppStatus) {
                return true
            }
        }

        return false
    }

    override fun isOpenSourceSelected() = appLoungePreference.isOpenSourceSelected()
}
