/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.os.BatteryManager
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.data.Constants
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fusedDownload.FusedDownloadRepository
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.utils.NetworkStatusManager
import foundation.e.apps.utils.StorageComputer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class DumpAppInstallStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var fusedDownloadRepository: FusedDownloadRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == null) {
            return
        }

        MainScope().launch {
            val gson = Gson()
            val appList = fusedDownloadRepository.getDownloadList()
            val appInstallStatusLog = "App install status: ${gson.toJson(appList)}"
            val deviceStatusLog = getDeviceInfo(context)
            val downloadStatusLog = getDownloadStatus(appList)

            Timber.tag(Constants.TAG_APP_INSTALL_STATE)
                .e("%s\n\n%s\n\n%s", deviceStatusLog, appInstallStatusLog, downloadStatusLog)
        }
    }

    private fun getDeviceInfo(context: Context?): String? {
        context?.let {
            val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            return "Available Space: ${StorageComputer.calculateAvailableDiskSpace()}" +
                    "\nInternet: ${
                        NetworkStatusManager.init(context).value
                    }\nBattery level: $batteryLevel"
        }

        return null
    }

    private fun getDownloadStatus(appList: List<FusedDownload>): String {
        var downloadStatusLog = ""
        appList.forEach {
            if (listOf(Status.DOWNLOADING, Status.DOWNLOADED).contains(it.status)) {
                it.downloadIdMap.keys.forEach { downloadId ->
                    val downloadStatus = downloadManager.isDownloadSuccessful(downloadId)
                    downloadStatusLog += "DownloadStatus: ${it.name}: Id: $downloadId: ${downloadStatus.second}\n"
                }
            }
        }

        return downloadStatusLog
    }
}
