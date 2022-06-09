package foundation.e.apps.manager.download.data

import android.app.DownloadManager
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import foundation.e.apps.manager.fused.FusedManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        val hasActiveObservers = hasActiveObservers()

        launch {
            while (hasActiveObservers) {
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
                    downloadManager.query(downloadManagerQuery.setFilterById(*downloadingIds.toLongArray()))
                        .use { cursor ->
                            cursor.moveToFirst()
                            while (!cursor.isAfterLast) {
                                val id =
                                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                                val status =
                                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                val totalSizeBytes =
                                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                val bytesDownloadedSoFar =
                                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))

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
                } catch (e: Exception) {
                    Log.e(TAG, "downloading Ids: $downloadingIds ${e.localizedMessage}")
                }
                delay(20)
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
