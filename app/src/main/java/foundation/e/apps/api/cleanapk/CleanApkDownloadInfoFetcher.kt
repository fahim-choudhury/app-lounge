package foundation.e.apps.api.cleanapk

import foundation.e.apps.api.cleanapk.data.download.Download
import retrofit2.Response

interface CleanApkDownloadInfoFetcher {
    suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any? = null): Response<Download>
}