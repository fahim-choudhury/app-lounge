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

package foundation.e.apps.install.pkg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.faultyApps.FaultyAppRepository
import foundation.e.apps.data.install.AppManagerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
@DelicateCoroutinesApi
open class PkgManagerBR : BroadcastReceiver() {

    @Inject
    lateinit var appManagerWrapper: AppManagerWrapper

    @Inject
    lateinit var appLoungePackageManager: AppLoungePackageManager

    @Inject
    lateinit var faultyAppRepository: FaultyAppRepository

    @Inject
    @Named("ioCoroutineScope")
    lateinit var coroutineScope: CoroutineScope

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (context != null && action != null) {
            val isUpdating = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -69)
            val packageName = intent.data?.schemeSpecificPart

            Timber.d("onReceive: $packageName $action $extra $status")
            packageName?.let {
                handlePackageList(it, action, isUpdating, extra)
            }
        }
    }

    private fun handlePackageList(
        packageName: String,
        action: String,
        isUpdating: Boolean,
        extra: String?
    ) {
        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                updateDownloadStatus(packageName)
                removeFaultyAppByPackageName(packageName)
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!isUpdating) deleteDownload(packageName)
                removeFaultyAppByPackageName(packageName)
            }

            AppLoungePackageManager.ERROR_PACKAGE_INSTALL -> {
                Timber.e("Installation failed due to error: $extra")
                updateInstallationIssue(packageName)
            }
        }
    }

    private fun removeFaultyAppByPackageName(pkgName: String) {
        coroutineScope.launch {
            faultyAppRepository.deleteFaultyAppByPackageName(pkgName)
        }
    }

    private fun deleteDownload(pkgName: String) {
        GlobalScope.launch {
            val fusedDownload = appManagerWrapper.getFusedDownload(packageName = pkgName)
            appManagerWrapper.cancelDownload(fusedDownload, pkgName)
        }
    }

    // TODO: FIND A BETTER WAY TO DO THIS
    private fun updateDownloadStatus(pkgName: String) {
        if (pkgName.isEmpty()) {
            Timber.d("updateDownloadStatus: package name should not be empty!")
        }
        GlobalScope.launch {
            val fusedDownload = appManagerWrapper.getFusedDownload(packageName = pkgName)
            appLoungePackageManager.setFakeStoreAsInstallerIfNeeded(fusedDownload)
            appManagerWrapper.updateDownloadStatus(fusedDownload, Status.INSTALLED)
        }
    }

    private fun updateInstallationIssue(pkgName: String) {
        GlobalScope.launch {
            val fusedDownload = appManagerWrapper.getFusedDownload(packageName = pkgName)
            appManagerWrapper.installationIssue(fusedDownload)
        }
    }
}
