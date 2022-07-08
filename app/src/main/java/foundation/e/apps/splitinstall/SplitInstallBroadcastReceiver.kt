/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021-2022  E FOUNDATION
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

package foundation.e.apps.splitinstall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.manager.pkg.PkgManagerModule
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SplitInstallBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SplitInstallBroadcastReceiver"

        const val ACTION_INSTALL_MODULE = "foundation.e.apps.splitinstall.INSTALL_MODULE"
        const val EXTRA_INSTALL_PACKAGE_NAME = "foundation.e.apps.splitinstall.INSTALL_PACKAGE_NAME"
        const val EXTRA_INSTALL_MODULE_NAME = "foundation.e.apps.splitinstall.INSTALL_MODULE_NAME"

        const val ACTION_MODULE_INSTALLED = "foundation.e.apps.splitinstall.MODULE_INSTALLED"
        const val EXTRA_MODULE_INSTALLED_STATUS = "android.content.pm.extra.STATUS"
        const val EXTRA_MODULE_INSTALLED_PACKAGE_NAME = "android.content.pm.extra.PACKAGE_NAME"
    }

    @Inject
    lateinit var packageManagerModule: PkgManagerModule

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {
            ACTION_INSTALL_MODULE -> handleInstallModule(context, intent.extras!!)
            ACTION_MODULE_INSTALLED ->  handleModuleInstalled(context, intent.extras!!)
        }
    }

    private fun handleInstallModule(context: Context, extras: Bundle) {
        val packageName = extras.getString(EXTRA_INSTALL_PACKAGE_NAME)
        val moduleName = extras.getString(EXTRA_INSTALL_MODULE_NAME)

        if (packageName == null) {
            Log.w(TAG, "Could not install module. Package name is null.")
            return
        }

        if (moduleName == null) {
            Log.w(TAG, "Could not install module. Module name is null.")
            return
        }

        packageManagerModule.installSplitModule(File(moduleName), packageName)

        SplitInstallNotification.showPendingModule(context, packageName)
    }

    private fun handleModuleInstalled(context: Context, extras: Bundle) {
        val status = extras.getInt(EXTRA_MODULE_INSTALLED_STATUS)
        val packageName = extras.getString(EXTRA_MODULE_INSTALLED_PACKAGE_NAME)

        SplitInstallNotification.showModuleInstalled(context, packageName!!, status)
    }
}
