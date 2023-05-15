package foundation.e.apps.api


interface DownloadInfoFetcher {
    suspend fun getDownloadInfo(idOrPackageName: String, versionCode: Any? = null, offerType: Int = -1): Any
}