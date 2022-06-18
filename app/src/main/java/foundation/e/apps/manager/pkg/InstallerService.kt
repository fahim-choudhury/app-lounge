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

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.manager.fused.FusedManagerRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InstallerService : Service() {

    @Inject
    lateinit var fusedManagerRepository: FusedManagerRepository

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    companion object {
        const val TAG = "InstallerService"
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -69)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        postStatus(status, packageName, extra)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun postStatus(status: Int, packageName: String?, extra: String?) {
        Log.d(TAG, "postStatus: $status $packageName $extra")
        if (status != PackageInstaller.STATUS_SUCCESS) {
            updateInstallationIssue(packageName ?: "")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun updateInstallationIssue(pkgName: String) {
        if (pkgName.isEmpty()) {
            Log.d(TAG, "updateDownloadStatus: package name should not be empty!")
        }
        GlobalScope.launch {
            val fusedDownload = fusedManagerRepository.getFusedDownload(packageName = pkgName)
            fusedManagerRepository.installationIssue(fusedDownload)
        }
    }
}
