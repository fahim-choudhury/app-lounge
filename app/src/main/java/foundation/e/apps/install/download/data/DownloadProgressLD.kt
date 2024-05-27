/*
 * Copyright ECORP SAS 2022
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.install.download.data

import android.app.DownloadManager
import android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
import android.app.DownloadManager.COLUMN_ID
import android.app.DownloadManager.COLUMN_STATUS
import android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import foundation.e.apps.data.fusedDownload.FusedManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class DownloadProgressLD @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadManagerQuery: DownloadManager.Query,
    private val fusedManagerRepository: FusedManagerRepository
) : LiveData<DownloadProgress>(), CoroutineScope {

    private lateinit var job: Job
    private var downloadProgress = DownloadProgress()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    override fun observe(owner: LifecycleOwner, observer: Observer<in DownloadProgress>) {
        job = Job()
        super.observe(owner, observer)
        launch {
            while (hasActiveObservers() || owner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                val downloads = fusedManagerRepository.getDownloadList()
                val downloadingList =
                    downloads.map { it.downloadIdMap }.filter { it.values.contains(false) }
                val downloadingIds = mutableListOf<Long>()
                downloadingList.forEach { downloadingIds.addAll(it.keys) }
                if (downloadingIds.isEmpty()) {
                    delay(500)
                    continue
                }
                try {
                    findDownloadProgress(downloadingIds)
                } catch (e: Exception) {
                    Timber.e("downloading Ids: $downloadingIds ${e.localizedMessage}")
                }
                delay(20)
            }
        }
    }

    private fun findDownloadProgress(downloadingIds: MutableList<Long>) {
        downloadManager.query(downloadManagerQuery.setFilterById(*downloadingIds.toLongArray()))
            .use { cursor ->
                cursor.moveToFirst()
                while (!cursor.isAfterLast) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
                    val totalSizeBytes =
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_SIZE_BYTES))
                    val bytesDownloadedSoFar =
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_BYTES_DOWNLOADED_SO_FAR))

                    downloadProgress.downloadId = id

                    if (!downloadProgress.totalSizeBytes.containsKey(id) ||
                        downloadProgress.totalSizeBytes[id] != totalSizeBytes
                    ) {
                        downloadProgress.totalSizeBytes[id] = totalSizeBytes
                    }

                    if (!downloadProgress.bytesDownloadedSoFar.containsKey(id) ||
                        downloadProgress.bytesDownloadedSoFar[id] != bytesDownloadedSoFar
                    ) {
                        downloadProgress.bytesDownloadedSoFar[id] = bytesDownloadedSoFar
                    }

                    downloadProgress.status[id] =
                        status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED

                    if (downloadingIds.size == cursor.count) {
                        postValue(downloadProgress)
                    }

                    if (downloadingIds.isEmpty()) {
                        cancel()
                    }
                    cursor.moveToNext()
                }
            }
    }

    override fun onInactive() {
        super.onInactive()
        job.cancel()
    }

    companion object {

        const val TAG = "DownloadProgressLD"

        var downloadId = mutableListOf<Long>()

        fun setDownloadId(id: Long) {
            if (id == -1L) {
                clearDownload()
                return
            }
            downloadId.add(id)
        }

        private fun clearDownload() {
            downloadId.clear()
        }
    }
}
