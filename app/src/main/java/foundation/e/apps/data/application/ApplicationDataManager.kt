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

package foundation.e.apps.data.application

import com.aurora.gplayapi.Constants
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.Status
import foundation.e.apps.install.pkg.PwaManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationDataManager @Inject constructor(
    private val appLoungePackageManager: AppLoungePackageManager,
    private val pwaManager: PwaManager
) {
    fun updateFilterLevel(application: Application) {
        application.filterLevel = getAppFilterLevel(application)
    }

    fun prepareApps(
        appList: List<Application>,
        list: MutableList<Home>,
        value: String
    ) {
        if (appList.isNotEmpty()) {
            appList.forEach {
                it.updateType()
                updateStatus(it)
                updateFilterLevel(it)
            }
            list.add(Home(value, appList))
        }
    }

    fun getAppFilterLevel(application: Application): FilterLevel {
        return when {
            application.package_name.isBlank() -> FilterLevel.UNKNOWN
            !application.isFree && application.price.isBlank() -> FilterLevel.UI
            application.source == Source.PWA || application.source == Source.OPEN_SOURCE -> FilterLevel.NONE
            application.source == Source.GITLAB_RELEASES -> FilterLevel.NONE
            !isRestricted(application) -> FilterLevel.NONE
            application.originalSize == 0L -> FilterLevel.UI
            else -> FilterLevel.NONE
        }
    }

    private fun isRestricted(application: Application): Boolean {
        return application.restriction != Constants.Restriction.NOT_RESTRICTED
    }

    fun updateStatus(application: Application) {
        if (application.status != Status.INSTALLATION_ISSUE) {
            application.status = getFusedAppInstallationStatus(application)
        }
    }

    /*
     * Get fused app installation status.
     * Applicable for both native apps and PWAs.
     *
     * Recommended to use this instead of [AppLoungePackageManager.getPackageStatus].
     */
    fun getFusedAppInstallationStatus(application: Application): Status {
        return if (application.is_pwa) {
            pwaManager.getPwaStatus(application)
        } else {
            appLoungePackageManager.getPackageStatus(
                application.package_name,
                application.latest_version_code,
            )
        }
    }
}
