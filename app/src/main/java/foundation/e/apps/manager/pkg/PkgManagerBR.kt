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

package foundation.e.apps.manager.pkg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.manager.fused.FusedManagerRepository
import foundation.e.apps.utils.enums.Status
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@DelicateCoroutinesApi
open class PkgManagerBR : BroadcastReceiver() {

    companion object {
        private const val TAG = "PkgManagerBR"
    }

    @Inject
    lateinit var fusedManagerRepository: FusedManagerRepository

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (context != null && action != null) {
            val packageUid = intent.getIntExtra(Intent.EXTRA_UID, 0)
            val isUpdating = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val packages = context.packageManager.getPackagesForUid(packageUid)
            val extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -69)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

            Timber.d( "onReceive: $packageName $action $extra $status")
            packages?.let { pkgList ->
                pkgList.forEach { pkgName ->
                    when (action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            updateDownloadStatus(pkgName)
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> {
                            if (!isUpdating) deleteDownload(pkgName)
                        }
                        PkgManagerModule.ERROR_PACKAGE_INSTALL -> {
                            Log.e(TAG, "Installation failed due to error: $extra")
                            updateInstallationIssue(pkgName)
                        }
                    }
                }
            }
        }
    }

    private fun deleteDownload(pkgName: String) {
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = pkgName)
            fusedManagerRepository.cancelDownload(fusedDownload)
        }
    }

    // TODO: FIND A BETTER WAY TO DO THIS
    private fun updateDownloadStatus(pkgName: String) {
        if (pkgName.isEmpty()) {
            Log.d("PkgManagerBR", "updateDownloadStatus: package name should not be empty!")
        }
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = pkgName)
            pkgManagerModule.setFakeStoreAsInstallerIfNeeded(fusedDownload)
            fusedManagerRepository.updateDownloadStatus(fusedDownload, Status.INSTALLED)
        }
    }

    private fun updateInstallationIssue(pkgName: String) {
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = pkgName)
            fusedManagerRepository.installationIssue(fusedDownload)
        }
    }
}
