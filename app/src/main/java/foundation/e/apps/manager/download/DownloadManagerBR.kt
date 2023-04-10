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
import androidx.annotation.UiThread
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@DelicateCoroutinesApi
class DownloadManagerBR : BroadcastReceiver() {

    @Inject
    lateinit var downloadManagerUtils: DownloadManagerUtils

    companion object {
        private const val TAG = "DownloadManagerBR"
    }

    @UiThread
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (context != null && action != null) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            Timber.i("onReceive: DownloadBR $action $id")
            when (action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    downloadManagerUtils.updateDownloadStatus(id)
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                    if (id != 0L) downloadManagerUtils.cancelDownload(id)
                }
            }
        }
    }
}
