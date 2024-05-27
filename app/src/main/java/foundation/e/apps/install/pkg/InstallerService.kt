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
import foundation.e.apps.data.faultyApps.FaultyAppRepository
import foundation.e.apps.data.application.UpdatesDao
import foundation.e.apps.data.install.AppManagerWrapper
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class InstallerService : Service() {

    @Inject
    lateinit var appManagerWrapper: AppManagerWrapper

    @Inject
    lateinit var appLoungePackageManager: AppLoungePackageManager

    @Inject
    lateinit var faultyAppRepository: FaultyAppRepository

    companion object {
        const val TAG = "InstallerService"
        const val INSTALL_FAILED_UPDATE_INCOMPATIBLE = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -69)
        var packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        /**
         There is some error case where package name from PackageInstaller remains
         empty (example: INSTALL_PARSE_FAILED_NOT_APK).
         the packageName from AppLoungePackageManager will be used in this error case.
         */
        val packageNamePackageManagerModule = intent.getStringExtra(AppLoungePackageManager.PACKAGE_NAME)

        packageName = packageName ?: packageNamePackageManagerModule
        postStatus(status, packageName, extra)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun postStatus(status: Int, packageName: String?, extra: String?) {
        Timber.d("postStatus: $status $packageName $extra")
        if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdatesDao.removeUpdateIfExists(packageName ?: "")
            return
        }

        Timber.e("App install is failed for: $packageName status: $status extra: $extra")
        updateInstallationIssue(packageName ?: "")
        if (status == PackageInstaller.STATUS_FAILURE_CONFLICT && extra?.contains(
                INSTALL_FAILED_UPDATE_INCOMPATIBLE
            ) == true
        ) {
            handleInstallFailureDueToSignatureMismatch(packageName)
        }
    }

    private fun handleInstallFailureDueToSignatureMismatch(packageName: String?) {
        MainScope().launch {
            if (packageName.isNullOrEmpty()) {
                Timber.wtf("Installation failure for an app without packagename!")
                return@launch
            }
            EventBus.invokeEvent(AppEvent.SignatureMissMatchError(packageName))
            faultyAppRepository.addFaultyApp(packageName, INSTALL_FAILED_UPDATE_INCOMPATIBLE)
            Timber.e("INSTALL_FAILED_UPDATE_INCOMPATIBLE for $packageName")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateInstallationIssue(pkgName: String) {
        if (pkgName.isEmpty()) {
            Timber.d("updateDownloadStatus: package name should not be empty!")
        }
        GlobalScope.launch {
            val fusedDownload = appManagerWrapper.getFusedDownload(packageName = pkgName)
            appManagerWrapper.installationIssue(fusedDownload)
        }
    }
}
