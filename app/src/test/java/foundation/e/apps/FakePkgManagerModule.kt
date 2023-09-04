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

package foundation.e.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.updates.UpdatesManagerImpl.Companion.PACKAGE_NAME_ANDROID_VENDING
import foundation.e.apps.data.updates.UpdatesManagerImpl.Companion.PACKAGE_NAME_F_DROID
import foundation.e.apps.install.pkg.PkgManagerModule

class FakePkgManagerModule(
    context: Context,
    val gplayApps: List<FusedApp>
) : PkgManagerModule(context) {

    val applicationInfo = mutableListOf(
        ApplicationInfo().apply { this.packageName = "foundation.e.demoone" },
        ApplicationInfo().apply { this.packageName = "foundation.e.demotwo" },
        ApplicationInfo().apply { this.packageName = "foundation.e.demothree" }
    )

    override fun getAllUserApps(): List<ApplicationInfo> {
        return applicationInfo
    }

    override fun getInstallerName(packageName: String): String {
        val gplayPackageNames = gplayApps.map { it.package_name }

        return if (gplayPackageNames.contains(packageName)) {
            PACKAGE_NAME_ANDROID_VENDING
        } else {
            PACKAGE_NAME_F_DROID
        }
    }
}
