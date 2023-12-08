package foundation.e.apps.data.application

import foundation.e.apps.data.cleanapk.data.download.Download
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import retrofit2.Response

interface DownloadInfoApi {

    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): String?

    suspend fun updateFusedDownloadWithDownloadingInfo(
        origin: Origin,
        fusedDownload: FusedDownload
    )

    suspend fun getOSSDownloadInfo(id: String, version: String?): Response<Download>
}
