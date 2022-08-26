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

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.ISplitInstallService
import foundation.e.apps.api.DownloadManager
import foundation.e.apps.api.fused.FusedAPIRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SplitInstallBinder(
    val context: Context,
    private val coroutineScope: CoroutineScope,
    val fusedAPIRepository: FusedAPIRepository,
    val downloadManager: DownloadManager,
    val authData: AuthData?,
    private var splitInstallSystemService: foundation.e.splitinstall.ISplitInstallService?
) : ISplitInstallService.Stub() {

    private val modulesToInstall = HashMap<String, String>()

    companion object {
        const val TAG = "SplitInstallerBinder"
    }

    override fun installSplitModule(packageName: String, moduleName: String) {
        if (authData == null) {
            Timber.i("No authentication data. Could not install on demand module")
            return
        }

        coroutineScope.launch {
            downloadModule(packageName, moduleName)
        }
    }

    fun setService(service: foundation.e.splitinstall.ISplitInstallService) {
        splitInstallSystemService = service
        installPendingModules()
    }

    private suspend fun downloadModule(packageName: String, moduleName: String) {
        withContext(Dispatchers.IO) {
            val versionCode = getPackageVersionCode(packageName)
            val url = fusedAPIRepository.getOnDemandModule(
                authData!!, packageName, moduleName,
                versionCode, 1
            ) ?: return@withContext

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
        val applicationInfo = context.packageManager.getPackageInfo(packageName, 0)
        return applicationInfo.versionCode
    }

    private fun installPendingModules() {
        for (module in modulesToInstall.keys) {
            val packageName = modulesToInstall[module]
            splitInstallSystemService?.installSplitModule(packageName, module)
        }
    }
}
