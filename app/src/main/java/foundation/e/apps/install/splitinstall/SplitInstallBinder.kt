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

package foundation.e.apps.install.splitinstall

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import foundation.e.apps.ISplitInstallService
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SplitInstallBinder(
    val context: Context,
    private val coroutineScope: CoroutineScope,
    val applicationRepository: ApplicationRepository,
    val downloadManager: DownloadManager,
    val authenticatorRepository: AuthenticatorRepository,
    private var splitInstallSystemService: foundation.e.splitinstall.ISplitInstallService?
) : ISplitInstallService.Stub() {

    private val modulesToInstall = HashMap<String, String>()

    companion object {
        const val TAG = "SplitInstallerBinder"
        const val AUTH_DATA_ERROR_MESSAGE = "Could not get auth data"
    }

    override fun installSplitModule(packageName: String, moduleName: String) {
        try {
            if (authenticatorRepository.gplayAuth == null) {
                Timber.w(AUTH_DATA_ERROR_MESSAGE)
                return
            }

            coroutineScope.launch {
                downloadModule(packageName, moduleName)
            }
        } catch (exception: GPlayLoginException) {
            Timber.w("$AUTH_DATA_ERROR_MESSAGE $exception")
        }
    }

    fun setService(service: foundation.e.splitinstall.ISplitInstallService) {
        splitInstallSystemService = service
        installPendingModules()
    }

    private suspend fun downloadModule(packageName: String, moduleName: String) {
        withContext(Dispatchers.IO) {
            val versionCode = getPackageVersionCode(packageName)
            val url = fetchModuleUrl(packageName, moduleName, versionCode)

            if (url == null) {
                Timber.e("Could not find split module named $moduleName for $packageName package")
                return@withContext
            }

            downloadManager.downloadFileInExternalStorage(
                url, packageName, "$packageName.split.$moduleName.apk"
            ) { success, path ->
                if (success) {
                    Timber.i("Split module has been downloaded: $path")
                    if (splitInstallSystemService == null) {
                        Timber.i("Not connected to system service now. Adding $path to the list.")
                        modulesToInstall[path] = packageName
                    }
                    splitInstallSystemService?.installSplitModule(packageName, path)
                }
            }
        }
    }

    private fun getPackageVersionCode(packageName: String): Int {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val longVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        return longVersionCode.toInt()
    }

    private suspend fun fetchModuleUrl(
        packageName: String,
        moduleName: String,
        versionCode: Int
    ): String? {
        var url = applicationRepository.getOnDemandModule(packageName, moduleName, versionCode, 1)

        if (url == null) {
            url = applicationRepository.getOnDemandModule(
                packageName,
                "config.$moduleName",
                versionCode,
                1
            )
        }

        return url
    }

    private fun installPendingModules() {
        for (module in modulesToInstall.keys) {
            val packageName = modulesToInstall[module]
            splitInstallSystemService?.installSplitModule(packageName, module)
        }
    }
}
