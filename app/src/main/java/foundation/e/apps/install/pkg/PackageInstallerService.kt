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

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import foundation.e.apps.data.enums.Status
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@DelicateCoroutinesApi
class PackageInstallerService : Service() {

    @Inject
    lateinit var fusedManagerRepository: FusedManagerRepository

    private val TAG = PackageInstallerService::class.java.simpleName

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -69)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (packageName != null) {
            if (status == PackageInstaller.STATUS_SUCCESS) {
                updateDownloadStatus(packageName)
            } else {
                Timber.e("Installation failed due to error: $extra")
                updateInstallationIssue(packageName)
            }
        }

        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // TODO: FIND A BETTER WAY TO DO THIS
    private fun updateDownloadStatus(pkgName: String) {
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = pkgName)
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
