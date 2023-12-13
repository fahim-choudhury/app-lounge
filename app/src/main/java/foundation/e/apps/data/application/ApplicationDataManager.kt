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
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ApplicationDataManager @Inject constructor(
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    private val appLoungePackageManager: AppLoungePackageManager,
    private val pwaManager: PWAManager
) {
    suspend fun updateFilterLevel(authData: AuthData?, application: Application) {
        application.filterLevel = getAppFilterLevel(application, authData)
    }

    suspend fun prepareApps(
        appList: List<Application>,
        list: MutableList<Home>,
        value: String
    ) {
        if (appList.isNotEmpty()) {
            appList.forEach {
                it.updateType()
                updateStatus(it)
                updateFilterLevel(null, it)
            }
            list.add(Home(value, appList))
        }
    }

    suspend fun getAppFilterLevel(application: Application, authData: AuthData?): FilterLevel {
        return when {
            application.package_name.isBlank() -> FilterLevel.UNKNOWN
            !application.isFree && application.price.isBlank() -> FilterLevel.UI
            application.origin == Origin.CLEANAPK -> FilterLevel.NONE
            !isRestricted(application) -> FilterLevel.NONE
            authData == null -> FilterLevel.UNKNOWN // cannot determine for gplay app
            !isApplicationVisible(application) -> FilterLevel.DATA
            application.originalSize == 0L -> FilterLevel.UI
            !isDownloadable(application) -> FilterLevel.UI
            else -> FilterLevel.NONE
        }
    }

    private fun isRestricted(application: Application): Boolean {
        return application.restriction != Constants.Restriction.NOT_RESTRICTED
    }

    /*
     * Some apps are simply not visible.
     * Example: com.skype.m2
     */
    private suspend fun isApplicationVisible(application: Application): Boolean {
        return kotlin.runCatching { gplayRepository.getAppDetails(application.package_name) }.isSuccess
    }

    /*
     * Some apps are visible but not downloadable.
     * Example: com.riotgames.league.wildrift
     */
    private suspend fun isDownloadable(application: Application): Boolean {
        return kotlin.runCatching {
            gplayRepository.getDownloadInfo(
                application.package_name,
                application.latest_version_code,
                application.offer_type,
            )
        }.isSuccess
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
            appLoungePackageManager.getPackageStatus(application.package_name, application.latest_version_code)
        }
    }
}
