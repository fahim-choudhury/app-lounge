package foundation.e.apps.data.cleanapk

import foundation.e.apps.data.cleanapk.data.download.Download
import retrofit2.Response

interface CleanApkDownloadInfoFetcher {
    suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any? = null): Response<Download>
}