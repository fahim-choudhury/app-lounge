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

package foundation.e.apps.manager.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import javax.inject.Inject

@AndroidEntryPoint
@DelicateCoroutinesApi
class DownloadManagerBR : BroadcastReceiver() {

    @Inject
    lateinit var downloadManagerUtils: DownloadManagerUtils
    private val TAG = DownloadManagerBR::class.simpleName

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (context != null && action != null) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            Log.d(TAG, "onReceive: $action")
            when (action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    if (downloadManagerUtils.downloadStatus(id) == DownloadManager.STATUS_SUCCESSFUL) {
                        downloadManagerUtils.checkAndUpdateStatus(id)
                    }
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                    if (id != 0L) downloadManagerUtils.cancelDownload(id)
                }
            }
        }
    }
}
