package foundation.e.apps.install.download.data

data class DownloadProgress(
    var totalSizeBytes: MutableMap<Long, Long> = mutableMapOf(),
    var bytesDownloadedSoFar: MutableMap<Long, Long> = mutableMapOf(),
    var status: MutableMap<Long, Boolean> = mutableMapOf(),
    var downloadId: Long = -1
)
